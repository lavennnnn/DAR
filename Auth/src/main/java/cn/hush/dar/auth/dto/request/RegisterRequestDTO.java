package cn.hush.dar.auth.dto.request;


import lombok.Data;

/**
 * @program: DAR
 * @description: 注册
 * @author: Hush
 * @create: 2025-11-22 17:06
 **/
@Data
public class RegisterRequestDTO {

    private String username;
    private String password;
    private String nickname;

}
