package com.omes.dgw.device.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "设备采集配置中的单个点位")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DevicePointConfigItem {

    @Schema(description = "点位编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "点位编码不能为空")
    @Size(max = 64, message = "点位编码长度不能超过64")
    private String code;

    @Schema(description = "点位名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "点位名称不能为空")
    @Size(max = 200, message = "点位名称长度不能超过200")
    private String name;

    @Schema(description = "读写控制类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "读写控制类型不能为空")
    private PointAccessType accessType;

    @Schema(description = "实际值点位（采集地址；只读/读写必填）")
    @Size(max = 200, message = "实际值点位长度不能超过200")
    private String actualAddress;

    @Schema(description = "设定值点位（写入地址；只写/读写必填）")
    @Size(max = 200, message = "设定值点位长度不能超过200")
    private String setpointAddress;

    @Schema(description = "数据类型", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "数据类型不能为空")
    private PointDataType dataType;

    @JsonIgnore
    @Schema(description = "PLC 类型名（只读，由 dataType 推导）")
    private String dataTypeLabel;

    @JsonIgnore
    @Schema(description = "数据类型中文说明（只读，由 dataType 推导）")
    private String dataTypeDescription;

    @Schema(description = "字节序（浮点/32位整型等多寄存器类型；空则使用设备默认）")
    private ModbusByteOrder byteOrder;

    @Schema(description = "文本长度（字符数，仅 String；不填默认 2）")
    @Min(value = 1, message = "字符串长度至少为1")
    @Max(value = 250, message = "字符串长度不能超过250")
    private Integer stringLength;

    @Schema(description = "缩放系数（不支持缩放的数据类型由服务端置为 1）")
    private BigDecimal scaleFactor;

    @Schema(description = "工程量下限（可选；不填则读写不做下限限制）")
    private BigDecimal engValueMin;

    @Schema(description = "工程量上限（可选；不填则读写不做上限限制）")
    private BigDecimal engValueMax;

    @Schema(description = "工程单位")
    private String engUnit;

    @Schema(description = "小数位数（不支持小数位的数据类型由服务端置为类型默认值）")
    @Min(value = 0, message = "小数位不能小于0")
    @Max(value = 10, message = "小数位不能大于10")
    private Integer decimalPlaces;

    @Schema(description = "BCD转码（Packed BCD：采集 0x1234→1234，写入 1234→0x1234；不支持时由服务端清空）")
    @JsonDeserialize(using = BcdConvertDeserializer.class)
    private Boolean bcdConvert;

    @Schema(description = "位域：A/B/C/D（各4bit）或 BA/DC（各8bit，别名 AB/CD）；BCD=DCBA 布局")
    @Size(max = 8, message = "位域长度不能超过8")
    private String bitField;

    /** 由 bitField 推导，一般无需手填；兼容旧配置 */
    @Schema(description = "位域起始位（由 bitField 推导）", hidden = true)
    private Integer bitOffset;

    /** 由 bitField 推导，一般无需手填；兼容旧配置 */
    @Schema(description = "位域长度（由 bitField 推导）", hidden = true)
    private Integer bitLength;
}
