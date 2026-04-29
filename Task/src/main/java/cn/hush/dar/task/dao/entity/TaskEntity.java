package cn.hush.dar.task.dao.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_task")
public class TaskEntity {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String name;
    private Integer priority;
    private Integer neededAntennas;
    private Integer neededCpuCores;
    private Integer neededGpuMem;
    private Double beamFrequency;
    private String beamGroup;
    private String preferredSurface;
    private String antennaScheduleMode;
    private Integer deadlineMs;
    private Boolean allowCrossSurface;
    private Integer targetReuseLimit;
    private String computeScheduleMode;
    private String dependsOnTaskIds;
    private String repelTaskIds;
    private Integer duration;
    private Integer remainingSeconds;
    private Double virtualShare;
    private Integer status;
    private Date createTime;
    private Date startTime;
    private Date endTime;
}
