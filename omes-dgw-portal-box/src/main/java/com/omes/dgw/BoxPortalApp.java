package com.omes.dgw;

import com.omes.dgw.portal.AbstractDgwPortal;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 边缘盒子门户：与中控相同的协议采集；实时缓存用 Caffeine；不写 Influx（无历史落库）。
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
