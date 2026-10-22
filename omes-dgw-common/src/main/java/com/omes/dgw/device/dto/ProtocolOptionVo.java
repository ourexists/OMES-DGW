package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Schema(description = "枚举选项（code + PLC 类型名 + 中文说明 + 是否已实现）")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProtocolOptionVo {

    @Schema(description = "枚举编码")
    private String code;

    @Schema(description = "PLC 类型名")
    private String label;

    @Schema(description = "中文说明")
    private String description;

    @Schema(description = "平台是否已实现采集、连接测试等能力")
    private boolean supported;

    @Schema(description = "是否支持配置工程值下限、工程值上限（均可选；不填则读写不做范围限制）")
    private Boolean supportsEngRange;

    @Schema(description = "是否支持配置 Modbus 多寄存器字节序")
    private Boolean supportsByteOrder;

    @Schema(description = "是否支持配置缩放系数")
    private Boolean supportsScaleFactor;

    @Schema(description = "是否支持配置小数位数")
    private Boolean supportsDecimalPlaces;

    @Schema(description = "是否支持 BCD 转码（无符号整型；Packed BCD：0x1234→1234）")
    private Boolean supportsBcdConvert;

    @Schema(description = "是否支持位域拆分（bitField：A/B/C/D/BA/DC）")
    private Boolean supportsBitField;

    @Schema(description = "类型标称位宽（位域校验用）")
    private Integer bitWidth;

    @Schema(description = "默认工程值下限（可选参考值；点位可不填，不填则读写不限制）")
    private BigDecimal defaultEngValueMin;

    @Schema(description = "默认工程值上限（可选参考值；点位可不填，不填则读写不限制）")
    private BigDecimal defaultEngValueMax;

    @Schema(description = "默认字节序编码")
    private String defaultByteOrder;

    @Schema(description = "默认缩放系数")
    private BigDecimal defaultScaleFactor;

    @Schema(description = "默认小数位数")
    private Integer defaultDecimalPlaces;

    @Schema(description = "默认是否开启 BCD 转码")
    private Boolean defaultBcdConvert;

    public ProtocolOptionVo(String code, String label, boolean supported) {
        this(code, label, null, supported);
    }

    public ProtocolOptionVo(String code, String label, String description, boolean supported) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.supported = supported;
    }

    public static ProtocolOptionVo fromDataType(PointDataType type) {
        ProtocolOptionVo vo = new ProtocolOptionVo(
                type.getCode(),
                type.getLabel(),
                type.getDescription(),
                true
        );
        vo.setSupportsEngRange(type.isSupportsEngRange());
        vo.setSupportsByteOrder(type.isSupportsByteOrder());
        vo.setSupportsScaleFactor(type.isSupportsScaleFactor());
        vo.setSupportsDecimalPlaces(type.isSupportsDecimalPlaces());
        vo.setSupportsBcdConvert(type.isSupportsBcdConvert());
        vo.setSupportsBitField(type.isSupportsBitField());
        vo.setBitWidth(type.getBitWidth());
        vo.setDefaultEngValueMin(type.getDefaultEngValueMin());
        vo.setDefaultEngValueMax(type.getDefaultEngValueMax());
        ModbusByteOrder byteOrder = type.getDefaultByteOrder();
        vo.setDefaultByteOrder(byteOrder != null ? byteOrder.getCode() : null);
        vo.setDefaultScaleFactor(type.getDefaultScaleFactor());
        vo.setDefaultDecimalPlaces(type.getDefaultDecimalPlaces());
        vo.setDefaultBcdConvert(type.getDefaultBcdConvert());
        return vo;
    }
}
