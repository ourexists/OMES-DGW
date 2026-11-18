package com.omes.dgw.deploy.logmonitor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@Schema(description = "日志文件摘要")
public class LogFileVo {
    private String name;
    private Long size;
    private Instant modifiedAt;
}
