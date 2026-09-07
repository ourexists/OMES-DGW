package com.omes.dgw.modscan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "ModScan 远程扫描单点")
public class ModscanScanItemVo {

    @Schema(description = "区内 0 基 PDU 偏移（与轮询、惯例号关系：4x 偏移 0 = 40001）")
    private int offset;

    @Schema(description = "PLC4X 地址（与设备轮询 convertLegacyAddress 一致），如 holding-register:1")
    private String address;

    @Schema(description = "Modbus 惯例地址（与点位台账一致），如 40001")
    private String legacyAddress;

    @Schema(description = "16 位寄存器二进制（高位在前）；线圈/离散输入为 0 或 1（不依赖点位数据类型）")
    private String binaryValue;
}
