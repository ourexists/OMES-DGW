package com.omes.dgw.deploy.logmonitor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@Schema(description = "日志内容")
public class LogContentVo {
    private String name;
    private Long size;
    private Instant modifiedAt;
    private List<String> lines;
    /** 下次增量拉取起点（字节偏移） */
    private Long nextOffset;
    private boolean truncated;
}
