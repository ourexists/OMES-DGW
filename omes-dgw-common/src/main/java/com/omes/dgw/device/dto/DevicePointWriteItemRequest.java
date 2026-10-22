package com.omes.dgw.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "批量控制写入中的单条指令。二选一：传 code，或传 address+dataType")
@Data
public class DevicePointWriteItemRequest {

    @Schema(description = "任务内执行序号；不传则在本批内按提交顺序在该 taskId 下递增")
    private Integer seqNo;

    @Schema(description = "设备ID；不传则使用批量请求中的 deviceId")
    private String deviceId;

    @Schema(description = "写入点位编码（与采集配置 points[].code 一致）；传入后自动解析地址/数据类型/字节序/缩放系数")
    @JsonAlias("pointCode")
    private String code;

    @Schema(description = "协议写入地址；未传 code 时必填")
    private String address;

    @Schema(description = "数据类型；未传 code 时必填")
    private PointDataType dataType;

    @Schema(description = "缩放系数（可选；传 code 时默认取配置值）")
    private BigDecimal scaleFactor;

    @Schema(description = "Modbus 字节序；不传则使用批量请求 byteOrder、点位配置、设备默认或 CDAB")
    private ModbusByteOrder byteOrder;

    @Schema(description = "写入值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "写入值不能为空")
    private Object value;
}
