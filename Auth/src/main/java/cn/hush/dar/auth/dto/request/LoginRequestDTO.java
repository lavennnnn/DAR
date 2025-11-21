package cn.hush.dar.auth.dto.request;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @program: DAR
 * @description: 登录请求参数实体
 * @author: Hush
 * @create: 2025-11-21 20:42
 **/
@Data
@Schema(description = "登录请求参数实体")
public class LoginRequestDTO {

    private String username;

    private String password;

}
