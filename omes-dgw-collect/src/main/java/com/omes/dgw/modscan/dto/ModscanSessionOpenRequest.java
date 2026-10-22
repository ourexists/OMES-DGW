package com.omes.dgw.modscan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "打开 ModScan 会话（DefaultPlcDriverManager 建连并保持）")
public class ModscanSessionOpenRequest {

    @Schema(description = "设备主键 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
}
