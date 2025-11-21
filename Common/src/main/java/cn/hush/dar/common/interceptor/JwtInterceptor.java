package cn.hush.dar.common.interceptor;


import cn.hush.dar.common.context.BaseContext;
import cn.hush.dar.common.utils.jwtutils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * @program: DAR
 * @description: jwt令牌校验的拦截器
 * @author: Hush
 * @create: 2025-11-22 01:36
 **/
@RequiredArgsConstructor
@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        String token = request.getHeader("Authorization");

        try {
            log.info("jwt校验:{}", token);
            Claims claims = jwtUtil.parseToken(token);
            Integer userId = (Integer) claims.get("userId");
            BaseContext.setCurrentId(userId);
            return true;
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }
}
