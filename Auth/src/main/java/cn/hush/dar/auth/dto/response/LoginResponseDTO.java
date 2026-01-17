package cn.hush.dar.auth.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * @program: DAR
 * @description: 登录响应参数实体
 * @author: Hush
 * @create: 2025-11-21 20:42
 **/
@Data
@Schema(description = "登录响应参数实体")
@Builder
public class LoginResponseDTO {

    private String token;

    private String username;

    private String nickname;

    private Integer userId;

}
