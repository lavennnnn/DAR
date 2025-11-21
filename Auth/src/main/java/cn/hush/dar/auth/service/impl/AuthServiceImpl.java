package cn.hush.dar.auth.service.impl;


import cn.hush.dar.auth.dao.entity.UserEntity;
import cn.hush.dar.auth.dao.mapper.UserMapper;
import cn.hush.dar.auth.dto.request.LoginRequestDTO;
import cn.hush.dar.auth.dto.response.LoginResponseDTO;
import cn.hush.dar.auth.service.AuthService;
import cn.hush.dar.common.constant.MessageConstant;
import cn.hush.dar.common.exception.ClientException;
import cn.hush.dar.common.exception.ServiceException;
import cn.hush.dar.common.utils.jwtutils.JwtUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static cn.hush.dar.common.constant.MessageConstant.ACCOUNT_NOT_FOUND;

/**
 * @program: DAR
 * @description:
 * @author: Hush
 * @create: 2025-11-21 20:49
 **/
@Service
@RequiredArgsConstructor
public class AuthServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    @Override
    public LoginResponseDTO login(LoginRequestDTO requestParam) {

        String username = requestParam.getUsername();
        String password = requestParam.getPassword();

        QueryWrapper query =  QueryWrapper.create()
                        .eq("username", username);

        UserEntity user = userMapper.selectOneByQuery(query);
        if (user == null) {
            throw new ClientException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        if(!passwordEncoder.matches(password,user.getPassword())) {
            throw new ClientException(MessageConstant.PASSWORD_ERROR);
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        LoginResponseDTO loginResponseDTO = new LoginResponseDTO();
        loginResponseDTO.setToken(token);
        loginResponseDTO.setNickname(user.getNickname());
        loginResponseDTO.setUserId(user.getId());

        return loginResponseDTO;
    }


}
