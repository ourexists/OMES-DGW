package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.PointDataType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "点位列元数据（表头）")
@Data
public class DevicePointHistoryPointMetaVo {

    @Schema(description = "点位名称")
    private String pointName;

    @Schema(description = "点位编码")
    private String pointCode;

    @Schema(description = "采集地址")
    private String address;

    @Schema(description = "工程单位")
    private String unit;

    @Schema(description = "数据类型")
    private PointDataType dataType;
}
