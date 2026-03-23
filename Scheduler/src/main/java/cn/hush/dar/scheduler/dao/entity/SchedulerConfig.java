package cn.hush.dar.scheduler.dao.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.util.Date;

@Data
@Table("t_scheduler_config")
public class SchedulerConfig {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String strategy;
    private Date updateTime;
}
