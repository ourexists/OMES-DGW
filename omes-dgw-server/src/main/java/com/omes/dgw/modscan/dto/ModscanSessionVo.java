package com.omes.dgw.modscan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "ModScan 会话")
public class ModscanSessionVo {

    @Schema(description = "会话 ID")
    private String sessionId;

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "从站号（Unit ID）")
    private Integer unitId;

    @Schema(description = "连接目标 host:port")
    private String connectionTarget;

    @Schema(description = "是否成功")
    private boolean success;

    @Schema(description = "状态说明")
    private String message;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "无扫描请求时会话最长保持时间（秒）")
    private long maxIdleSeconds = 3600;
}
