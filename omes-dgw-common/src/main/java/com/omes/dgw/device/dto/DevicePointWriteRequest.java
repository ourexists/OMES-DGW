package com.omes.dgw.device.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "单条控制写入请求。二选一：传 code（按采集配置点位编码解析），或传 address+dataType（直接指定地址）")
@Data
public class DevicePointWriteRequest {

    @Schema(description = "调用方虚拟任务标识，同标识下指令按 seqNo 顺序执行", requiredMode = Schema.RequiredMode.REQUIRED, example = "task-001")
    @NotBlank(message = "任务标识不能为空")
    private String taskId;

    @Schema(description = "任务内执行序号；不传则在该 taskId 下自动递增")
    private Integer seqNo;

    @Schema(description = "设备ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "设备ID不能为空")
    private String deviceId;

    @Schema(description = "写入点位编码（与采集配置 points[].code 一致）；传入后自动解析地址/数据类型/字节序/缩放系数，此时可不传 address/dataType")
    @JsonAlias("pointCode")
    private String code;

    @Schema(description = "协议写入地址；未传 code 时必填", example = "40001")
    private String address;

    @Schema(description = "数据类型；未传 code 时必填")
    private PointDataType dataType;

    @Schema(description = "缩放系数（可选；传 code 时默认取配置值，写入前将值除以该系数）")
    private BigDecimal scaleFactor;

    @Schema(description = "Modbus 多寄存器字节序 ABCD/BADC/CDAB/DCBA；传 code 时默认取配置值，再回落到设备默认/CDAB")
    private ModbusByteOrder byteOrder;

    @Schema(description = "写入值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "写入值不能为空")
    private Object value;
}
