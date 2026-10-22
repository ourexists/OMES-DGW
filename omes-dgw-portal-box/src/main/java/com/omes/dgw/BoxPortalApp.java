package com.omes.dgw;

import com.omes.dgw.portal.AbstractDgwPortal;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 边缘盒子门户：Caffeine 实时缓存，不做协议采集（{@code app.collect.enabled=false}）。
 */
@SpringBootApplication(
        scanBasePackages = "com.omes.dgw",
        excludeName = {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        }
)
@ConfigurationPropertiesScan
@MapperScan("com.omes.dgw.*.mapper")
@EnableScheduling
public class BoxPortalApp extends AbstractDgwPortal {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BoxPortalApp.class);
        application.setDefaultProperties(prepareDefaultProperties());
        application.run(args);
    }
}
