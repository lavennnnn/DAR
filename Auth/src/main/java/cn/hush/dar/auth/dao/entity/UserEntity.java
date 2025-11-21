package cn.hush.dar.auth.dao.entity;


import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import jdk.jfr.DataAmount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @program: DAR
 * @description: 用户实体
 * @author: Hush
 * @create: 2025-11-21 18:47
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("user")
public class UserEntity {

    @Id(keyType = KeyType.Auto)
    private int id;

    private String username;

    private String password;

    private String nickname;

    private Date lastLoginTime;

    @Column(onInsertValue = "now()")
    private Date createTime;

    @Column(onUpdateValue = "now()", onInsertValue = "now()")
    private Date updateTime;

}
