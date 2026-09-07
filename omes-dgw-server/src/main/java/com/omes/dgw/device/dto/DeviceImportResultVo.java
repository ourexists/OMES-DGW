package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "设备 Excel 导入结果")
@Data
public class DeviceImportResultVo {

    @Schema(description = "新增设备数")
    private int createdDevices;

    @Schema(description = "更新设备数")
    private int updatedDevices;

    @Schema(description = "导入点位总数")
    private int importedPoints;

    @Schema(description = "结果摘要")
    private String message;
}
