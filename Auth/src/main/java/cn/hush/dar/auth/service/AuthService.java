package cn.hush.dar.auth.service;

import cn.hush.dar.auth.dao.entity.UserEntity;
import cn.hush.dar.auth.dto.request.LoginRequestDTO;
import cn.hush.dar.auth.dto.request.RegisterRequestDTO;
import cn.hush.dar.auth.dto.response.LoginResponseDTO;
import cn.hush.dar.auth.dto.response.RegisterResponseDTO;
import com.mybatisflex.core.service.IService;

public interface AuthService extends IService<UserEntity> {

    /**
     * 登录接口
     * @param requestParam
     * @return
     */
    LoginResponseDTO login(LoginRequestDTO requestParam);

    /**
     * 注册接口
     * @param requestParam
     */
    RegisterResponseDTO register(RegisterRequestDTO requestParam);
}
