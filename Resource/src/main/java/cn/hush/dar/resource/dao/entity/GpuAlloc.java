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
 * @description: 任务-GPU 分配记录
 * @author: Hush
 * @create: 2026-03-19
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_gpu_alloc")
public class GpuAlloc {
    @Id(keyType = KeyType.Auto)
    private Long id;
    private Integer taskId;
    private Integer gpuId;
    private Integer mem;
    private Date createTime;
}
