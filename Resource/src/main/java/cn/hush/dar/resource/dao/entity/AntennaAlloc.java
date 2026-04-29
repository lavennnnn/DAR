package cn.hush.dar.resource.dao.entity;

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
 * @description: 任务-天线分配记录
 * @author: Hush
 * @create: 2026-03-25
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_antenna_unit_alloc")
public class AntennaAlloc {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Integer taskId;
    private Integer antennaId;
    private Double beamFrequency;
    private String beamGroup;
    private Date createTime;
}
