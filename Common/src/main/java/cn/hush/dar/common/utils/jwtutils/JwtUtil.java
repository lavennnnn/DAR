package cn.hush.dar.common.utils.jwtutils;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @program: DAR
 * @description: JWT 工具类（Spring Boot 组件，自动加载密钥）
 * @author: Hush
 * @create: 2025-11-21 21:45
 **/
@Component
public class JwtUtil {

    private final SecretKey secretKey = JwtSecretKeyManager.getOrGenerateSecretKey();

    @Value("${jwt.expire-time:7200000}")
    private long expireTime;

    public String generateToken(String username, Integer id) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("userId", id);

        return Jwts.builder()
                .setClaims(claims)
                .expiration(new Date(System.currentTimeMillis() + expireTime))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证并解析 Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secretKey)
                    .setAllowedClockSkewSeconds(30)
                    .build()
                    .parseClaimsJws(token).getBody();
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new RuntimeException("Token 已过期", e);
        } catch (Exception e) {
            throw new RuntimeException("Token 验证失败", e);
        }
    }
}
