package cn.hush.dar.auth.service.impl;


import cn.hush.dar.auth.dao.entity.UserEntity;
import cn.hush.dar.auth.dao.mapper.UserMapper;
import cn.hush.dar.auth.dto.request.LoginRequestDTO;
import cn.hush.dar.auth.dto.request.RegisterRequestDTO;
import cn.hush.dar.auth.dto.response.LoginResponseDTO;
import cn.hush.dar.auth.dto.response.RegisterResponseDTO;
import cn.hush.dar.auth.service.AuthService;
import cn.hush.dar.common.constant.MessageConstant;
import cn.hush.dar.common.exception.ClientException;
import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.utils.jwtutils.JwtUtil;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.Assert;

import java.util.Date;

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

        //查询用户是否已注册
        UserEntity user = userMapper.selectOneByQuery(query);
        if (user == null) {
            throw new ClientException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //判断密码正误
        if(!passwordEncoder.matches(password,user.getPassword())) {
            throw new ClientException(MessageConstant.PASSWORD_ERROR);
        }

        //生成token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        //更新最近登录时间
        UpdateChain.of(UserEntity.class)
                .set(UserEntity::getLastLoginTime, new Date())
                .where(UserEntity::getId).eq(user.getId())
                .update();

        return LoginResponseDTO.builder()
                .token(token)
                .nickname(user.getNickname())
                .userId(user.getId()).
                build();
    }

    @Override
    public RegisterResponseDTO register(RegisterRequestDTO requestParam) {
        String username = requestParam.getUsername();
        String password = requestParam.getPassword();

        //判断用户是否已经注册
        long count = userMapper.selectCountByQuery(
                QueryWrapper.create().eq(UserEntity::getUsername, username)
        );

        if (count > 0) {
            throw new ClientException("用户名已存在");
        }

        //加密明文密码
        String encodePassword = passwordEncoder.encode(password);

        UserEntity user = UserEntity.builder()
                .username(username)
                .password(encodePassword)
                .nickname(requestParam.getNickname())
                .build();

        //新用户落库
        userMapper.insert(user);

        //生成token
        String token = jwtUtil.generateToken(user.getUsername(), user.getId());

        return RegisterResponseDTO.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .username(username)
                .token(token)
                .build();
    }


}
