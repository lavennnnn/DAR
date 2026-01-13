package cn.hush.dar.task.dao.entity;


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
 * @description:
 * @author: Hush
 * @create: 2026-01-06 01:41
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_task")
public class TaskEntity {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String name;
    //数值越大优先级越高
    private Integer priority;
    private Integer neededAntennas;
    private Integer neededCpuCores;
    private Integer neededGpuMem;
    private Integer duration; // 模拟任务耗时(秒)

    // 0:PENDING, 1:RUNNING, 2:COMPLETED, 3:FAILED
    private Integer status;

    private Date createTime;
    private Date startTime;
    private Date endTime;
}
