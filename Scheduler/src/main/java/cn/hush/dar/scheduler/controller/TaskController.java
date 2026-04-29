package cn.hush.dar.scheduler.controller;


import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.resource.service.ResourceService;
import cn.hush.dar.scheduler.dao.entity.ScheduleLog;
import cn.hush.dar.scheduler.dao.mapper.ScheduleLogMapper;
import cn.hush.dar.scheduler.websocket.WebSocketServer;
import cn.hush.dar.task.dao.entity.TaskEntity;
import cn.hush.dar.task.service.TaskService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;


@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    private final ResourceService resourceService;
    private final ScheduleLogMapper scheduleLogMapper;

    @PostMapping("/submit")
    public Result<Integer> submitTask(@RequestBody TaskEntity task) {
        task.setStatus(0);
        task.setCreateTime(new Date());

        if(task.getPriority() == null) task.setPriority(1);
        if(task.getNeededAntennas() == null) task.setNeededAntennas(4);
        if(task.getDuration() == null) task.setDuration(10);
        if (task.getRemainingSeconds() == null) task.setRemainingSeconds(task.getDuration());
        if (task.getVirtualShare() == null) task.setVirtualShare(0.0);
        if (task.getBeamGroup() != null && task.getBeamGroup().isBlank()) task.setBeamGroup(null);
        if (task.getPreferredSurface() != null && task.getPreferredSurface().isBlank()) task.setPreferredSurface(null);
        task.setAntennaScheduleMode(null);
        task.setComputeScheduleMode(null);
        if (task.getDependsOnTaskIds() != null && task.getDependsOnTaskIds().isBlank()) task.setDependsOnTaskIds(null);
        if (task.getRepelTaskIds() != null && task.getRepelTaskIds().isBlank()) task.setRepelTaskIds(null);
        if (task.getAllowCrossSurface() == null) task.setAllowCrossSurface(true);
        if (task.getTargetReuseLimit() == null) task.setTargetReuseLimit(3);

        taskService.save(task);

        return Results.success(task.getId());
    }


    @PostMapping("/cancel")
    public Result<Boolean> cancelTask(@RequestParam Integer taskId) {
        TaskEntity task = taskService.getById(taskId);
        if (task == null) return Results.failure("TASK_NOT_FOUND", "Task not found");

        if (task.getStatus() == 2 || task.getStatus() == 3) {
            return Results.failure("TASK_FINISHED", "Task already finished");
        }

        if (task.getStatus() == 1) {
            resourceService.releaseResourcesByTask(taskId);
            resourceService.releaseCpuCores(taskId);
            resourceService.releaseGpuMem(taskId);
        }

        task.setStatus(3);
        task.setEndTime(new Date());
        taskService.updateById(task);

        String msg = String.format("{\"type\":\"TASK_CANCEL\", \"taskId\":%d}", taskId);
        WebSocketServer.sendInfo(msg);
        scheduleLogMapper.insert(ScheduleLog.builder()
                .taskId(taskId)
                .action("CANCEL")
                .detail("manual cancel")
                .createTime(new Date())
                .build());

        return Results.success(true);
    }

    @GetMapping("/list")
    public Result<List<TaskEntity>> list() {
        List<TaskEntity> tasks = taskService.queryChain()
                .orderBy(TaskEntity::getCreateTime, false)
                .list();
        return Results.success(tasks);
    }
    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public Result<Boolean> deletePastTask(@RequestParam Integer taskId) {
        TaskEntity task = taskService.getById(taskId);
        if (task == null) return Results.failure("TASK_NOT_FOUND", "Task not found");

        if (task.getStatus() != 2 && task.getStatus() != 3) {
            return Results.failure("TASK_NOT_FINISHED", "Only past tasks can be deleted");
        }

        scheduleLogMapper.deleteByQuery(
                QueryWrapper.create().eq(ScheduleLog::getTaskId, taskId)
        );

        boolean removed = taskService.removeById(taskId);
        return Results.success(removed);
    }
}

