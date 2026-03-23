package cn.hush.dar.scheduler.service;


import cn.hush.dar.resource.dao.entity.AntennaResource;
import cn.hush.dar.resource.dao.entity.CPUResource;
import cn.hush.dar.resource.dao.entity.GPUResource;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.scheduler.dao.entity.ScheduleLog;
import cn.hush.dar.scheduler.dao.mapper.ScheduleLogMapper;
import cn.hush.dar.scheduler.model.ScheduleStrategy;
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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ScheduleLogMapper scheduleLogMapper;
    private final SchedulerConfigService schedulerConfigService;
    private static final int TIME_SLICE_SECONDS = 5;
    private static final int RESOURCE_ALERT_STATUS = 2;
    private final Set<Integer> faultAntennaIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> faultCpuIds = ConcurrentHashMap.newKeySet();
    private final Set<Integer> faultGpuIds = ConcurrentHashMap.newKeySet();

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

        // 初始化持久化字段（首次调度）
        for (TaskEntity task : pendingTasks) {
            boolean updated = false;
            if (task.getRemainingSeconds() == null) {
                task.setRemainingSeconds(task.getDuration());
                updated = true;
            }
            if (task.getVirtualShare() == null) {
                task.setVirtualShare(0.0);
                updated = true;
            }
            if (updated) {
                taskService.updateById(task);
            }
        }

        ScheduleStrategy strategy = ScheduleStrategy.from(schedulerConfigService.getStrategy());

        // DRF: 计算系统总资源，用于估算每个任务的主导份额
        int totalAntennas = resourceService.getAllAntennas().size();
        int totalCpuCores = resourceService.getAllCPUs().stream()
                .mapToInt(c -> c.getTotalCores() == null ? 0 : c.getTotalCores())
                .sum();
        int totalGpuMem = resourceService.getAllGPUs().stream()
                .mapToInt(g -> g.getTotalMemory() == null ? 0 : g.getTotalMemory())
                .sum();

        // 可用资源快照（用于DRF循环中的快速可行性判断）
        int availAntennas = (int) resourceService.getAllAntennas().stream()
                .filter(a -> a.getStatus() == 0).count();
        int availCpu = resourceService.getAllCPUs().stream()
                .mapToInt(c -> Math.max(0, (c.getTotalCores() == null ? 0 : c.getTotalCores()) - (c.getUsedCores() == null ? 0 : c.getUsedCores())))
                .sum();
        int availGpu = resourceService.getAllGPUs().stream()
                .mapToInt(g -> Math.max(0, (g.getTotalMemory() == null ? 0 : g.getTotalMemory()) - (g.getUsedMemory() == null ? 0 : g.getUsedMemory())))
                .sum();

        Comparator<TaskEntity> taskComparator = buildComparator(strategy, totalAntennas, totalCpuCores, totalGpuMem);

        List<TaskEntity> queue = new ArrayList<>(pendingTasks);
        boolean allocatedInRound = true;
        while (!queue.isEmpty() && allocatedInRound) {
            allocatedInRound = false;
            queue.sort(taskComparator);

            for (int i = 0; i < queue.size(); i++) {
                TaskEntity task = queue.get(i);
                int needAnt = task.getNeededAntennas() == null ? 0 : task.getNeededAntennas();
                int needCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
                int needGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();

                if (needAnt <= availAntennas && needCpu <= availCpu && needGpu <= availGpu) {
                    boolean scheduled = tryAllocateResource(task);
                    if (scheduled) {
                        availAntennas -= needAnt;
                        availCpu -= needCpu;
                        availGpu -= needGpu;
                        double delta = computeDominantShare(task, totalAntennas, totalCpuCores, totalGpuMem);
                        task.setVirtualShare((task.getVirtualShare() == null ? 0.0 : task.getVirtualShare()) + delta);
                        if (task.getRemainingSeconds() == null) {
                            task.setRemainingSeconds(task.getDuration());
                        }
                        taskService.updateById(task);
                        queue.remove(i);
                        allocatedInRound = true;
                        break; // 每轮分配一个，更新资源后再排序
                    }
                }
            }
        }
    }

    /**
     * 尝试为单个任务分配资源
     */
    private boolean tryAllocateResource(TaskEntity task) {
        // 1. 检查资源池中是否有足够的空闲天线
        List<AntennaResource> freeAntennas = resourceService.getAllAntennas().stream()
                .filter(a -> a.getStatus() == 0) // 0 表示空闲
                .limit(task.getNeededAntennas())
                .collect(Collectors.toList());

        // 2. 资源不足判断
        if (freeAntennas.size() < task.getNeededAntennas()) {
            log.debug("任务[{}] 资源不足 (需 {}, 剩 {})，等待下一轮", task.getName(), task.getNeededAntennas(), freeAntennas.size());
            return false;
        }

        int neededCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        int neededGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();

        if (neededCpu > 0) {
            int availableCpu = resourceService.getAllCPUs().stream()
                    .mapToInt(c -> Math.max(0, c.getTotalCores() - c.getUsedCores()))
                    .sum();
            if (availableCpu < neededCpu) {
                log.debug("任务[{}] CPU 资源不足 (需 {}, 剩 {})，等待下一轮", task.getName(), neededCpu, availableCpu);
                return false;
            }
        }

        if (neededGpu > 0) {
            int availableGpu = resourceService.getAllGPUs().stream()
                    .mapToInt(g -> Math.max(0, g.getTotalMemory() - g.getUsedMemory()))
                    .sum();
            if (availableGpu < neededGpu) {
                log.debug("任务[{}] GPU 资源不足 (需 {}, 剩 {})，等待下一轮", task.getName(), neededGpu, availableGpu);
                return false;
            }
        }

        // 3. 执行分配 (原子操作)
        List<Integer> antennaIds = freeAntennas.stream().map(AntennaResource::getId).collect(Collectors.toList());
        boolean antennaOk = resourceService.allocateAntennas(antennaIds, task.getId());

        if (!antennaOk) {
            log.warn("FAILED: 任务[{}] 天线资源分配失败", task.getName());
            return false;
        }

        boolean cpuOk = resourceService.allocateCpuCores(task.getId(), neededCpu);
        if (!cpuOk) {
            resourceService.releaseResourcesByTask(task.getId());
            log.warn("FAILED: 任务[{}] CPU 资源分配失败", task.getName());
            return false;
        }

        boolean gpuOk = resourceService.allocateGpuMem(task.getId(), neededGpu);
        if (!gpuOk) {
            resourceService.releaseResourcesByTask(task.getId());
            resourceService.releaseCpuCores(task.getId());
            log.warn("FAILED: 任务[{}] GPU 资源分配失败", task.getName());
            return false;
        }

        // 4. 更新任务状态为 "运行中(1)"
        task.setStatus(1);
        task.setStartTime(new Date());
        if (task.getRemainingSeconds() == null) {
            task.setRemainingSeconds(task.getDuration());
        }
        taskService.updateById(task);

        log.info("SUCCESS: 任务[{}] 调度成功，已分配天线: {}", task.getName(), antennaIds);

        // 5. 推送消息给前端
        String msg = String.format("{\"type\":\"TASK_START\", \"taskId\":%d, \"antennas\":%s}",
                task.getId(), antennaIds.toString());
        WebSocketServer.sendInfo(msg);
        logSchedule(task.getId(), "SCHEDULE_START",
                String.format("antennas=%s,cpu=%d,gpu=%d",
                        antennaIds.toString(),
                        task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores(),
                        task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem()));
        return true;
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

        List<TaskEntity> pendingTasks = taskService.list(
                QueryWrapper.create().where(TaskEntity::getStatus).eq(0)
        );
        boolean shouldPreempt = false;
        if (!pendingTasks.isEmpty()) {
            int availAntennas = (int) resourceService.getAllAntennas().stream()
                    .filter(a -> a.getStatus() == 0).count();
            int availCpu = resourceService.getAllCPUs().stream()
                    .mapToInt(c -> Math.max(0, (c.getTotalCores() == null ? 0 : c.getTotalCores()) - (c.getUsedCores() == null ? 0 : c.getUsedCores())))
                    .sum();
            int availGpu = resourceService.getAllGPUs().stream()
                    .mapToInt(g -> Math.max(0, (g.getTotalMemory() == null ? 0 : g.getTotalMemory()) - (g.getUsedMemory() == null ? 0 : g.getUsedMemory())))
                    .sum();

            boolean canScheduleWithFree = false;
            for (TaskEntity t : pendingTasks) {
                int needAnt = t.getNeededAntennas() == null ? 0 : t.getNeededAntennas();
                int needCpu = t.getNeededCpuCores() == null ? 0 : t.getNeededCpuCores();
                int needGpu = t.getNeededGpuMem() == null ? 0 : t.getNeededGpuMem();
                if (needAnt <= availAntennas && needCpu <= availCpu && needGpu <= availGpu) {
                    canScheduleWithFree = true;
                    break;
                }
            }
            // 只有当有等待任务且空闲资源不足以调度任何一个等待任务时才抢占
            shouldPreempt = !canScheduleWithFree;
        }

        Date now = new Date();
        for (TaskEntity task : runningTasks) {
            // 计算是否达到预计持续时间
            long runTime = now.getTime() - task.getStartTime().getTime();
            int remaining = task.getRemainingSeconds() == null ? task.getDuration() : task.getRemainingSeconds();
            int slice = Math.min(TIME_SLICE_SECONDS, remaining);
            if (runTime >= slice * 1000L) {
                remaining -= slice;
                if (remaining <= 0) {
                    finishTask(task);
                } else {
                    if (shouldPreempt) {
                        // 时间片到期，动态反馈：释放资源并重新排队
                        resourceService.releaseResourcesByTask(task.getId());
                        resourceService.releaseCpuCores(task.getId());
                        resourceService.releaseGpuMem(task.getId());

                        task.setStatus(0);
                        task.setStartTime(null);
                        task.setRemainingSeconds(remaining);
                        taskService.updateById(task);

                        String msg = String.format("{\"type\":\"TASK_PREEMPT\", \"taskId\":%d}", task.getId());
                        WebSocketServer.sendInfo(msg);
                        logSchedule(task.getId(), "PREEMPT", "remainingSeconds=" + remaining);
                    } else {
                        // 没有其他等待任务，继续执行下一时间片（不抢占）
                        task.setRemainingSeconds(remaining);
                        task.setStartTime(new Date());
                        taskService.updateById(task);
                    }
                }
            }
        }
    }

    /**
     * 资源异常告警：检测状态为 2(故障/离线) 的资源并推送一次性告警
     */
    @Scheduled(fixedDelay = 5000)
    public void monitorResourceAlerts() {
        List<AntennaResource> antennas = resourceService.getAllAntennas();
        Set<Integer> currentAntennaFaults = antennas.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == RESOURCE_ALERT_STATUS)
                .map(AntennaResource::getId)
                .collect(Collectors.toSet());
        notifyNewFaults(currentAntennaFaults, faultAntennaIds, "ANTENNA");

        List<CPUResource> cpus = resourceService.getAllCPUs();
        Set<Integer> currentCpuFaults = cpus.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == RESOURCE_ALERT_STATUS)
                .map(CPUResource::getId)
                .collect(Collectors.toSet());
        notifyNewFaults(currentCpuFaults, faultCpuIds, "CPU");

        List<GPUResource> gpus = resourceService.getAllGPUs();
        Set<Integer> currentGpuFaults = gpus.stream()
                .filter(g -> g.getStatus() != null && g.getStatus() == RESOURCE_ALERT_STATUS)
                .map(GPUResource::getId)
                .collect(Collectors.toSet());
        notifyNewFaults(currentGpuFaults, faultGpuIds, "GPU");
    }

    private void notifyNewFaults(Set<Integer> currentFaults, Set<Integer> cache, String resourceType) {
        for (Integer id : currentFaults) {
            if (cache.add(id)) {
                String msg = String.format(
                        "{\"type\":\"ALERT\",\"level\":\"ERROR\",\"category\":\"RESOURCE_FAULT\",\"resourceType\":\"%s\",\"resourceId\":%d,\"time\":%d}",
                        resourceType, id, System.currentTimeMillis()
                );
                WebSocketServer.sendInfo(msg);
            }
        }
        cache.retainAll(currentFaults);
    }

    private void finishTask(TaskEntity task) {
        // 1. 释放资源
        resourceService.releaseResourcesByTask(task.getId());
        resourceService.releaseCpuCores(task.getId());
        resourceService.releaseGpuMem(task.getId());

        // 2. 更新任务状态为 "已完成(2)"
        task.setStatus(2);
        task.setEndTime(new Date());
        task.setRemainingSeconds(0);
        taskService.updateById(task);

        log.info("COMPLETED: 任务[{}] 执行结束，资源已释放", task.getName());

        // 推送消息给前端
        String msg = String.format("{\"type\":\"TASK_END\", \"taskId\":%d}", task.getId());
        WebSocketServer.sendInfo(msg);
        logSchedule(task.getId(), "COMPLETE", "endTime=" + task.getEndTime());

    }

    private void logSchedule(Integer taskId, String action, String detail) {
        scheduleLogMapper.insert(ScheduleLog.builder()
                .taskId(taskId)
                .action(action)
                .detail(detail)
                .createTime(new Date())
                .build());
    }

    private Comparator<TaskEntity> buildComparator(ScheduleStrategy strategy,
                                                   int totalAntennas,
                                                   int totalCpuCores,
                                                   int totalGpuMem) {
        if (strategy == ScheduleStrategy.PRIORITY) {
            return Comparator
                    .comparing(TaskEntity::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(TaskEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TaskEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        if (strategy == ScheduleStrategy.FCFS) {
            return Comparator
                    .comparing(TaskEntity::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TaskEntity::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        }
        return Comparator
                .comparingDouble((TaskEntity t) -> t.getVirtualShare() == null ? 0.0 : t.getVirtualShare())
                .thenComparingDouble((TaskEntity t) -> computeDominantShare(t, totalAntennas, totalCpuCores, totalGpuMem))
                .thenComparing(TaskEntity::getPriority, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    /**
     * DRF 主导份额计算：max(天线份额, CPU份额, GPU份额)
     */
    private double computeDominantShare(TaskEntity task, int totalAnt, int totalCpu, int totalGpu) {
        int needAnt = task.getNeededAntennas() == null ? 0 : task.getNeededAntennas();
        int needCpu = task.getNeededCpuCores() == null ? 0 : task.getNeededCpuCores();
        int needGpu = task.getNeededGpuMem() == null ? 0 : task.getNeededGpuMem();

        double antShare = (totalAnt <= 0) ? (needAnt > 0 ? Double.MAX_VALUE : 0.0) : (double) needAnt / totalAnt;
        double cpuShare = (totalCpu <= 0) ? (needCpu > 0 ? Double.MAX_VALUE : 0.0) : (double) needCpu / totalCpu;
        double gpuShare = (totalGpu <= 0) ? (needGpu > 0 ? Double.MAX_VALUE : 0.0) : (double) needGpu / totalGpu;

        return Math.max(antShare, Math.max(cpuShare, gpuShare));
    }
}
