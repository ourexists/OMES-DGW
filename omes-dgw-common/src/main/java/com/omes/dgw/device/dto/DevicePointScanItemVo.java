package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "点位扫描结果（单条）")
public class DevicePointScanItemVo {

    @Schema(description = "点位编码")
    private String code;

    @Schema(description = "点位名称")
    private String name;

    @Schema(description = "实际值点位（采集地址）")
    private String address;

    @Schema(description = "数据类型")
    private PointDataType dataType;

    @Schema(description = "PLC 类型名")
    private String dataTypeLabel;

    @Schema(description = "数据类型中文说明")
    private String dataTypeDescription;

    @Schema(description = "读写控制类型")
    private PointAccessType accessType;

    @Schema(description = "读写控制类型说明")
    private String accessTypeLabel;

    @Schema(description = "原始值")
    private Object rawValue;

    @Schema(description = "工程值（缩放/换算后）")
    private Object value;

    @Schema(description = "工程单位")
    private String unit;

    @Schema(description = "读取错误信息，成功时为空")
    private String error;
}
