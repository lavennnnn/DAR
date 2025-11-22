package cn.hush.dar.common.utils.jwtutils;


import io.jsonwebtoken.security.Keys;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @program: DAR
 * @description: JWT 密钥自动管理工具（无需手动生成/硬编码）
 * @author: Hush
 * @create: 2025-11-21 22:09
 **/

public class JwtSecretKeyManager {

    // 环境变量名（生产环境通过该变量注入密钥）
    private static final String ENV_JWT_SECRET = "JWT_SECRET_KEY";
    // 本地密钥文件路径（开发环境自动生成/读取）
    private static final String LOCAL_SECRET_FILE = "Common/src/main/resources/jwt-secret.key";
    // 密钥算法（HS256 要求 32 字节密钥）
    private static final String ALGORITHM = "HS256";


    /**
     * 自动获取/生成合规的 JWT 密钥
     */
    public static SecretKey getOrGenerateSecretKey() {
        // 1. 优先从环境变量读取（生产环境首选）
        String envSecret = System.getenv(ENV_JWT_SECRET);
        if (envSecret != null && !envSecret.trim().isEmpty()) {
            try {
                byte[] keyBytes = Base64.getDecoder().decode(envSecret.trim());
                validateKeyLength(keyBytes); // 验证密钥长度合规
                return Keys.hmacShaKeyFor(keyBytes);
            } catch (Exception e) {
                throw new RuntimeException("环境变量 JWT_SECRET_KEY 中的密钥格式非法（必须是 Base64 编码的 32字节数据）", e);
            }
        }

        // 2. 环境变量不存在，读取本地密钥文件
        File secretFile = new File(LOCAL_SECRET_FILE);
        if (secretFile.exists() && secretFile.length() > 0) {
            try {
                Resource resource = new ClassPathResource("jwt-secret.key");
                InputStream inputStream = resource.getInputStream();
                String fileSecret = new String(FileCopyUtils.copyToByteArray(inputStream), StandardCharsets.UTF_8).trim();
                byte[] keyBytes = Base64.getDecoder().decode(fileSecret);
                validateKeyLength(keyBytes);
                return Keys.hmacShaKeyFor(keyBytes);
            } catch (Exception e) {
                throw new RuntimeException("本地密钥文件 jwt-secret.key 读取失败或密钥非法", e);
            }
        }

        // 3. 本地文件不存在，自动生成密钥并写入文件
        SecretKey secretKey = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String base64Secret = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        try (FileOutputStream fos = new FileOutputStream(secretFile)) {
            fos.write(base64Secret.getBytes(StandardCharsets.UTF_8));
            System.out.println("✅ 本地密钥文件不存在，已自动生成并保存到：" + LOCAL_SECRET_FILE);
            System.out.println("⚠️  开发环境使用，生产环境请通过环境变量 JWT_SECRET_KEY 注入密钥");
        } catch (Exception e) {
            throw new RuntimeException("自动生成密钥后写入文件失败", e);
        }
        return secretKey;
    }


    /**
     * 验证密钥长度（HS256 要求 32 字节，HS512 要求 64 字节）
     */
    private static void validateKeyLength(byte[] keyBytes) {
        int requiredLength = ALGORITHM.equals("HS256") ? 32 : 64;
        if (keyBytes.length != requiredLength) {
            throw new IllegalArgumentException(ALGORITHM + " 算法要求密钥长度为 " + requiredLength + "字节，当前为 " + keyBytes.length + "字节");
        }
    }
}
