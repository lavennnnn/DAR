package cn.hush.dar.scheduler.controller;


import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.task.dao.entity.TaskEntity;
import cn.hush.dar.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

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

}
