package cn.hush.dar.resource.dao.entity;


import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2025-12-11 01:16
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("t_antenna") // 对应数据库表名
public class AntennaResource {
    @Id(keyType = KeyType.Auto)
    private Integer id;

    private String code;     // 阵元编号
    private Double xPos;     // X坐标
    private Double yPos;     // Y坐标

    /**
     * 数字阵列特有属性
     */
    private Double phase;     // 相位 (0~360度)
    private Double amplitude; // 幅度 (0.0~1.0)

    private Integer status;  // 0:空闲, 1:占用, 2:故障
    private Integer taskId;  // 占用该阵元的任务ID
}
