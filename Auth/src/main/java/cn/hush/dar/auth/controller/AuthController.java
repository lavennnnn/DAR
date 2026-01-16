package cn.hush.dar.auth.controller;



import cn.hush.dar.auth.dto.request.LoginRequestDTO;

import cn.hush.dar.auth.dto.request.RegisterRequestDTO;
import cn.hush.dar.auth.dto.response.LoginResponseDTO;

import cn.hush.dar.auth.dto.response.RegisterResponseDTO;
import cn.hush.dar.auth.service.impl.AuthServiceImpl;
import cn.hush.dar.common.result.Result;
import cn.hush.dar.common.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * @program: DAR
 * @description: 鉴权控制器
 * @author: Hush
 * @create: 2025-11-21 18:46
 **/
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
@Tag(name = "鉴权相关功能")
@Slf4j
public class AuthController {

    private final AuthServiceImpl authService;

    @Operation(summary = "用户登录")
    @RequestMapping (value = "login", method = RequestMethod.POST)
    public Result<LoginResponseDTO> login(@RequestBody LoginRequestDTO requestParam) {
        log.info("操作员登录:{}", requestParam);
        return Results.success(authService.login(requestParam));
    }

    @Operation(summary = "用户注册")
    @RequestMapping(value = "register", method = RequestMethod.POST)
    public Result<RegisterResponseDTO>  register(@RequestBody RegisterRequestDTO requestParam) {
        log.info("操作员注册:{}", requestParam);
        return Results.success(authService.register(requestParam));
    }

    @Operation(summary = "查询昵称")
    @RequestMapping(value = "getNickname", method =  RequestMethod.GET)
    public Result<String> getNickname(@RequestParam String name){
        return Results.success(authService.getNickname(name));
    }

}
