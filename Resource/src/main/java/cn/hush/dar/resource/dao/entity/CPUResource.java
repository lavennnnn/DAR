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
@Table("t_cpu")
public class CPUResource {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String hostname;
    private String ipAddress;   // IP地址，例如 "192.168.1.101"
    private Integer totalCores;
    private Integer usedCores;
    private Integer status;
}