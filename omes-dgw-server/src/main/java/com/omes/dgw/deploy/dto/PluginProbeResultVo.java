package com.omes.dgw.deploy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "连通性探测结果")
public class PluginProbeResultVo {
    private boolean ok;
    private String message;
    private long latencyMs;
}
