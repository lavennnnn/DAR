package cn.hush.dar.resource;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("cn.hush.dar.resource.dao.mapper")
@ComponentScan(basePackages = {
        "cn.hush.dar.resource",      // 包含 controller、service、mapper 等子包
        "cn.hush.dar.common"     // Common 模块（JwtUtil、拦截器等）)
})
public class ResourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResourceApplication.class, args);
    }

}
