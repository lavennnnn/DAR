package cn.hush.dar.scheduler.service;


import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.scheduler.websocket.WebSocketServer;
import cn.hush.dar.task.dao.entity.TaskEntity;

import cn.hush.dar.task.service.TaskService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2025-12-11 01:09
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSchedulerService {

    private final TaskService taskService;
    private final ResourceService resourceService;

    /**
     * 定时调度器：每 5 秒执行一次
     * 扫描待调度任务，尝试分配资源
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional(rollbackFor = Exception.class)
    public void schedulePendingTasks() {
        // 1. 构建查询条件：状态为 0 (待调度)，按优先级降序
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where(TaskEntity::getStatus).eq(0)
                .orderBy(TaskEntity::getPriority, false); // false = desc (降序)

        // 2. 使用 taskService 执行查询
        List<TaskEntity> pendingTasks = taskService.list(queryWrapper);

        if (pendingTasks.isEmpty()) return;

        log.info(">>> 开始调度循环，待处理任务数: {}", pendingTasks.size());

        for (TaskEntity task : pendingTasks) {
            tryAllocateResource(task);
        }
    }

    /**
     * 尝试为单个任务分配资源
     */
    private void tryAllocateResource(TaskEntity task) {
        // 1. 检查资源池中是否有足够的空闲天线
        List<AntennaResource> freeAntennas = resourceService.getAllAntennas().stream()
                .filter(a -> a.getStatus() == 0) // 0 表示空闲
                .limit(task.getNeededAntennas())
                .collect(Collectors.toList());

        // 2. 资源不足判断
        if (freeAntennas.size() < task.getNeededAntennas()) {
            log.debug("任务[{}] 资源不足 (需 {}, 剩 {})，等待下一轮", task.getName(), task.getNeededAntennas(), freeAntennas.size());
            return;
        }

        // 3. 执行分配 (原子操作)
        List<Integer> antennaIds = freeAntennas.stream().map(AntennaResource::getId).collect(Collectors.toList());
        boolean success = resourceService.allocateAntennas(antennaIds, task.getId());

        if (success) {
            // 4. 更新任务状态为 "运行中(1)"
            task.setStatus(1);
            task.setStartTime(new Date());
            taskService.updateById(task);

            log.info("SUCCESS: 任务[{}] 调度成功，已分配天线: {}", task.getName(), antennaIds);

            // 5. 推送消息给前端
            String msg = String.format("{\"type\":\"TASK_START\", \"taskId\":%d, \"antennas\":%s}",
                    task.getId(), antennaIds.toString());
            WebSocketServer.sendInfo(msg);
        } else {
            log.warn("FAILED: 任务[{}] 资源抢占失败", task.getName());
        }
    }

    /**
     * 定时监控器：每 1 秒检查一次
     * 模拟任务执行完成，释放资源
     */
    @Scheduled(fixedDelay = 1000)
    public void monitorRunningTasks() {
        // 获取所有 "运行中(1)" 的任务
        QueryWrapper runningQuery = QueryWrapper.create()
                .where(TaskEntity::getStatus).eq(1);

        List<TaskEntity> runningTasks = taskService.list(runningQuery);

        if (runningTasks == null || runningTasks.isEmpty()) return;

        Date now = new Date();
        for (TaskEntity task : runningTasks) {
            // 计算是否达到预计持续时间
            long runTime = now.getTime() - task.getStartTime().getTime();
            if (runTime >= task.getDuration() * 1000L) {
                finishTask(task);
            }
        }
    }

    private void finishTask(TaskEntity task) {
        // 1. 释放资源
        resourceService.releaseResourcesByTask(task.getId());

        // 2. 更新任务状态为 "已完成(2)"
        task.setStatus(2);
        task.setEndTime(new Date());
        taskService.updateById(task);

        log.info("COMPLETED: 任务[{}] 执行结束，资源已释放", task.getName());

        // 推送消息给前端
        String msg = String.format("{\"type\":\"TASK_END\", \"taskId\":%d}", task.getId());
        WebSocketServer.sendInfo(msg);
    }
}
