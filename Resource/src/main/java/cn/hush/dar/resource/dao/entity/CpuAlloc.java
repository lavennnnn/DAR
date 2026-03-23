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
 * @description: 任务-CPU 分配记录
 * @author: Hush
 * @create: 2026-03-19
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_cpu_alloc")
public class CpuAlloc {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Integer taskId;
    private Integer cpuId;
    private Integer cores;
    private Date createTime;
}
