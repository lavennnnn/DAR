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
@Table("t_antenna")
public class PhysicalAntennaResource {
    @Id(keyType = KeyType.Auto)
    private Integer id;
    private String code;
    private String name;
    private String surfaceCode;
    private Double xPos;
    private Double yPos;
    private Integer status;
}
