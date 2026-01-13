package cn.hush.dar.scheduler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {
        "cn.hush.dar.resource",
        "cn.hush.dar.common",     // Common 模块（JwtUtil、拦截器等）)
        "cn.hush.dar.scheduler",
        "cn.hush.dar.task"
})
@MapperScan("cn.hush.dar.*.dao.mapper")
@EnableScheduling // 开启定时任务支持
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }

}
