package com.omes.dgw.modscan.dto;

import com.omes.dgw.protocol.modbus.ModbusRegisterArea;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "ModScan 寄存器区扫描结果")
public class ModscanScanVo {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "寄存器区")
    private ModbusRegisterArea area;

    @Schema(description = "寄存器区说明")
    private String areaLabel;

    @Schema(description = "从站号（Unit ID）")
    private Integer unitId;

    @Schema(description = "连接目标 host:port")
    private String connectionTarget;

    @Schema(description = "本次扫描请求的起始值（惯例号或区内 PDU 偏移，与请求一致）")
    private int startAddress;

    @Schema(description = "请求数量")
    private int quantity;

    @Schema(description = "实际返回条数")
    private int returnedCount;

    @Schema(description = "是否读取成功")
    private boolean success;

    @Schema(description = "状态说明")
    private String message;

    @Schema(description = "耗时（毫秒）")
    private Long latencyMs;

    @Schema(description = "扫描时间")
    private LocalDateTime scanTime;

    @Schema(description = "扫描结果列表")
    private List<ModscanScanItemVo> items = new ArrayList<>();
}
