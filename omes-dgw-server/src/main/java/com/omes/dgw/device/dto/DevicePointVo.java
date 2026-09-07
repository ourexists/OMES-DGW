package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DevicePointVo {

    private String id;
    private String deviceId;
    private String deviceName;
    private String code;
    private String name;
    private PointAccessType accessType;
    private String accessTypeLabel;
    @Schema(description = "实际值点位")
    private String actualAddress;
    @Schema(description = "设定值点位")
    private String setpointAddress;
    private PointDataType dataType;
    @Schema(description = "PLC 类型名")
    private String dataTypeLabel;
    @Schema(description = "数据类型中文说明")
    private String dataTypeDescription;
    private BigDecimal scaleFactor;
    private BigDecimal engValueMin;
    private BigDecimal engValueMax;
    private Integer decimalPlaces;
    private Boolean bcdConvert;
    private String bitField;
    private Integer bitOffset;
    private Integer bitLength;
}
