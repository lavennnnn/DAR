package cn.hush.dar.auth.dto.response;


import lombok.Builder;
import lombok.Data;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2025-11-22 17:32
 **/
@Data
@Builder
public class RegisterResponseDTO {

    private Integer userId; // 新注册用户的 ID
    private String username; // 用户名
    private String nickname; // 昵称
    private String token;

}
