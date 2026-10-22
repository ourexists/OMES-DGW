package com.omes.dgw;

import com.omes.dgw.portal.AbstractDgwPortal;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 中控电脑门户：Redis + InfluxDB，完整采集能力。
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
public class CenterPortalApp extends AbstractDgwPortal {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(CenterPortalApp.class);
        application.setDefaultProperties(prepareDefaultProperties());
        application.run(args);
    }
}
