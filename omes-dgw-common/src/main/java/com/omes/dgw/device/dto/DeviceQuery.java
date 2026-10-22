package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.DeviceProtocol;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "设备分页查询条件")
@Data
public class DeviceQuery {

    @Schema(description = "设备名称（模糊匹配）")
    private String name;

    @Schema(description = "设备编码（模糊匹配）")
    private String code;

    @Schema(description = "通信协议")
    private DeviceProtocol protocol;

    @Schema(description = "所属分组编号（精确匹配）")
    private String categoryCode;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "是否附带本地实时缓存中的设备状态", example = "false")
    private Boolean includeRealtime;

    @Schema(description = "页码，从 1 开始", example = "1")
    private long page = 1;

    @Schema(description = "每页条数", example = "10")
    private long size = 10;
}
