package com.omes.dgw;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
        // 主应用无 redis 依赖时本排除无害；插件在 classpath 且 caffeine 模式时避免误连 Redis。
        // store-backend=redis 时由 omes-dgw-plugin-redis 的 RedisConfig @ImportAutoConfiguration 启用。
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
})
@ConfigurationPropertiesScan
@MapperScan("com.omes.dgw.*.mapper")
@EnableScheduling
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
