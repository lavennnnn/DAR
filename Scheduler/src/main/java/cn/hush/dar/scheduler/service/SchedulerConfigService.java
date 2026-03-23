package cn.hush.dar.scheduler.service;

import cn.hush.dar.scheduler.dao.entity.SchedulerConfig;
import cn.hush.dar.scheduler.dao.mapper.SchedulerConfigMapper;
import cn.hush.dar.scheduler.model.ScheduleStrategy;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SchedulerConfigService {

    private final SchedulerConfigMapper schedulerConfigMapper;

    public SchedulerConfig getConfig() {
        SchedulerConfig config = schedulerConfigMapper.selectOneByQuery(QueryWrapper.create());
        if (config == null) {
            config = new SchedulerConfig();
            config.setStrategy(ScheduleStrategy.DRF.getCode());
            schedulerConfigMapper.insert(config);
        }
        return config;
    }

    public String getStrategy() {
        return getConfig().getStrategy();
    }

    @Transactional(rollbackFor = Exception.class)
    public SchedulerConfig updateStrategy(String strategy) {
        ScheduleStrategy normalized = ScheduleStrategy.from(strategy);
        SchedulerConfig config = getConfig();
        config.setStrategy(normalized.getCode());
        schedulerConfigMapper.update(config);
        return config;
    }
}
