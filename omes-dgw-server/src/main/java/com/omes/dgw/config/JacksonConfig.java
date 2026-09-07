package com.omes.dgw.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * 统一 JSON 中 {@link LocalDateTime} 的序列化/反序列化格式，供 HTTP 响应及注入的 ObjectMapper 共用。
 * <p>
 * {@code spring.jackson.date-format} 仅作用于 {@link java.util.Date}，对 Java 8 时间类型无效。
 */
@Configuration
public class JacksonConfig {

    /** 与 application.yml 中 spring.jackson.date-format 保持一致 */
    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    @Bean
    Jackson2ObjectMapperBuilderCustomizer localDateTimeFormatCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
            builder.simpleDateFormat(DATE_TIME_PATTERN);
            builder.timeZone(TimeZone.getTimeZone("GMT+8"));
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToEnable(
                    DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
                    JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN
            );
        };
    }

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
