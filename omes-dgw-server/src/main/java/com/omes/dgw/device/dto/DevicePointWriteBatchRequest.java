package com.omes.dgw.device.dto;

import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "批量控制点位写入请求。条目可按 code 或 address+dataType 写入")
@Data
public class DevicePointWriteBatchRequest {

    @Schema(description = "调用方虚拟任务标识，本批指令同属该任务并按 seqNo/提交顺序执行", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "任务标识不能为空")
    private String taskId;

    @Schema(description = "本批默认设备ID；条目未指定 deviceId 时使用")
    private String deviceId;

    @Schema(description = "本批默认 Modbus 字节序；单条未指定 byteOrder 时使用")
    private ModbusByteOrder byteOrder;

    @NotEmpty(message = "写入项不能为空")
    @Valid
    private List<DevicePointWriteItemRequest> items;
}
