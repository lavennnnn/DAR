package cn.hush.dar.scheduler.controller;


import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.scheduler.websocket.WebSocketServer;
import cn.hush.dar.task.dao.entity.TaskEntity;
import cn.hush.dar.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2026-01-13 17:51
 **/

@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    private final ResourceService resourceService;

    /**
     * 提交新任务
     * Post Body: { "name": "雷达扫描任务A", "priority": 10, "neededAntennas": 16, "duration": 5 }
     */
    @PostMapping("/submit")
    public Result<Integer> submitTask(@RequestBody TaskEntity task) {
        // 1. 补全默认值
        task.setStatus(0);
        task.setCreateTime(new Date());

        // 如果没传，给点默认值防空指针
        if(task.getPriority() == null) task.setPriority(1);
        if(task.getNeededAntennas() == null) task.setNeededAntennas(4);
        if(task.getDuration() == null) task.setDuration(10);

        // 2. 保存到数据库
        taskService.save(task);

        return Results.success(task.getId());
    }


    @PostMapping("/cancel")
    public Result<Boolean> cancelTask(@RequestParam Integer taskId) {
        // 1. 查任务
        TaskEntity task = taskService.getById(taskId);
        if (task == null) return Results.failure("TASK_NOT_FOUND", "任务不存在");

        // 2. 如果已结束，不能取消
        if (task.getStatus() == 2 || task.getStatus() == 3) {
            return Results.failure("TASK_FINISHED", "任务已结束");
        }

        // 3. 如果正在运行(1)，需要释放资源
        if (task.getStatus() == 1) {
            // 这里需要注入 resourceService
            resourceService.releaseResourcesByTask(taskId);
        }

        // 4. 更新状态为 3 (Failed/Cancelled)
        task.setStatus(3);
        task.setEndTime(new Date());
        taskService.updateById(task);

        // 5. 关键：通知前端
        String msg = String.format("{\"type\":\"TASK_END\", \"taskId\":%d}", taskId);
        WebSocketServer.sendInfo(msg);

        return Results.success(true);
    }

    // ✅ 新增：获取任务列表接口
    @GetMapping("/list")
    public Result<List<TaskEntity>> list() {
        // 查询所有任务，按创建时间倒序排列（新任务在前）
        List<TaskEntity> tasks = taskService.queryChain()
                .orderBy(TaskEntity::getCreateTime, false) // false = desc
                .list();
        return Results.success(tasks);
    }

}
