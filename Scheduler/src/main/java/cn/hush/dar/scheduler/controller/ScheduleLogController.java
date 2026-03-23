package cn.hush.dar.scheduler.controller;

import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.scheduler.dao.entity.ScheduleLog;
import cn.hush.dar.scheduler.dao.mapper.ScheduleLogMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @program: DAR
 * @description: 调度日志查询接口
 * @author: Hush
 * @create: 2026-03-19
 **/
@RestController
@RequestMapping("/api/schedule-log")
@RequiredArgsConstructor
public class ScheduleLogController {

    private final ScheduleLogMapper scheduleLogMapper;

    /**
     * 查询指定任务的调度日志
     * GET /api/schedule-log/list?taskId=123
     */
    @GetMapping("/list")
    public Result<List<ScheduleLog>> list(@RequestParam Integer taskId) {
        List<ScheduleLog> logs = scheduleLogMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq(ScheduleLog::getTaskId, taskId)
                        .orderBy(ScheduleLog::getCreateTime, true)
        );
        return Results.success(logs);
    }
}
