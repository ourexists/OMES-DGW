package com.omes.dgw.modscan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "关闭 ModScan 会话")
public class ModscanSessionCloseRequest {

    @Schema(description = "打开会话时返回的 sessionId", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "sessionId不能为空")
    private String sessionId;
}
