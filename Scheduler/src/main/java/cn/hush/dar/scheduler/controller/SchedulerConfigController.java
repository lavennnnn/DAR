package cn.hush.dar.scheduler.controller;

import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import cn.hush.dar.scheduler.dao.entity.SchedulerConfig;
import cn.hush.dar.scheduler.model.ScheduleStrategy;
import cn.hush.dar.scheduler.service.SchedulerConfigService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scheduler/config")
@RequiredArgsConstructor
public class SchedulerConfigController {

    private final SchedulerConfigService schedulerConfigService;

    @GetMapping
    public Result<SchedulerConfigView> getConfig() {
        SchedulerConfig config = schedulerConfigService.getConfig();
        return Results.success(new SchedulerConfigView(
                config.getStrategy(),
                ScheduleStrategy.codes()
        ));
    }

    @PutMapping
    public Result<SchedulerConfigView> updateConfig(@RequestBody SchedulerConfigView request) {
        SchedulerConfig updated = schedulerConfigService.updateStrategy(request.getStrategy());
        return Results.success(new SchedulerConfigView(
                updated.getStrategy(),
                ScheduleStrategy.codes()
        ));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedulerConfigView {
        private String strategy;
        private List<String> supported;
    }
}
