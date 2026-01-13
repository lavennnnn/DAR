package cn.hush.dar.resource.dao.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_gpu")
public class GPUResource {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String model;        // 型号，例如 "NVIDIA RTX 4090"
    private Integer totalMemory; //GB
    private Integer usedMemory;
    private Integer status;
}