package cn.hush.dar.scheduler.dao.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @program: DAR
 * @description: 调度过程记录
 * @author: Hush
 * @create: 2026-03-19
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_schedule_log")
public class ScheduleLog {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Integer taskId;
    private String action; // SCHEDULE_START / PREEMPT / COMPLETE / CANCEL
    private String detail;
    private Date createTime;
}
