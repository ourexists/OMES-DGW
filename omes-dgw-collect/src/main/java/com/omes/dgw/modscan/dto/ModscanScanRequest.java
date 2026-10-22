package com.omes.dgw.modscan.dto;

import com.omes.dgw.codec.modbus.ModbusRegisterArea;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "ModScan 寄存器区扫描（在已打开会话上读取）")
public class ModscanScanRequest {

    @Schema(description = "会话 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "sessionId不能为空")
    private String sessionId;

    @Schema(description = "寄存器区", requiredMode = Schema.RequiredMode.REQUIRED, example = "HOLDING_REGISTER")
    @NotNull(message = "寄存器区不能为空")
    private ModbusRegisterArea area;

    @Schema(description = "起始：区内 0 基 PDU 偏移（0=首寄存器）或惯例号（如 40001，与点位地址一致）",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "40001")
    @Min(value = 0, message = "起始地址不能小于0")
    private int startAddress;

    @Schema(description = "读取数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @Min(value = 1, message = "数量至少为1")
    @Max(value = 8000, message = "数量不能超过8000")
    private int quantity;
}
