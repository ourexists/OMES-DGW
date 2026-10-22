package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "按设备 ID 列表批量操作")
@Data
public class DeviceIdsRequest {

    @Schema(description = "设备主键 ID 列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "设备ID列表不能为空")
    @Size(max = 200, message = "单次最多操作 200 台设备")
    private List<String> deviceIds;
}
