package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.DeviceProtocol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "设备新增/修改请求体")
@Data
public class DeviceDto {

    @Schema(description = "主键；新增可不传，修改必填")
    private String id;

    @Schema(description = "设备名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "名称不能为空")
    @Size(max = 200, message = "名称长度不能超过200")
    private String name;

    @Schema(description = "设备编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "编码不能为空")
    @Size(max = 64, message = "编码长度不能超过64")
    private String code;

    @Schema(description = "通信协议", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "协议不能为空")
    private DeviceProtocol protocol;

    @Schema(description = "所属分组编号（可选）")
    @Size(max = 64, message = "所属分组编号长度不能超过64")
    private String categoryCode;

    @Schema(description = "IP 地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "192.168.1.100")
    @NotBlank(message = "IP地址不能为空")
    @Pattern(
            regexp = "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$",
            message = "IP地址格式不正确"
    )
    private String ipAddress;

    @Schema(description = "端口号", requiredMode = Schema.RequiredMode.REQUIRED, example = "502")
    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号范围为1-65535")
    @Max(value = 65535, message = "端口号范围为1-65535")
    private Integer port;

    @Schema(description = "默认采集周期（秒）", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    @NotNull(message = "默认采集周期不能为空")
    @Min(value = 1, message = "默认采集周期至少为1秒")
    private Integer collectIntervalSec;

    @Schema(description = "连接超时时间（毫秒）", requiredMode = Schema.RequiredMode.REQUIRED, example = "3000")
    @NotNull(message = "连接超时时间不能为空")
    @Min(value = 500, message = "连接超时时间至少为500毫秒")
    private Integer connectTimeoutMs;

    @Schema(description = "是否共享查询线程（控制写入复用轮询连接池）", example = "false")
    private Boolean reusePollingConnection = false;

    @Schema(description = "协议站号/从站号（Modbus 从站、FINS 节点、SLMP 站号）")
    private Integer unitId;

    @Schema(
            description = "是否启用；新增默认停用。修改接口会忽略该字段，启停请调用 /device/realtime/enable|stop",
            example = "false"
    )
    private Boolean enabled = false;

    @Schema(description = "备注")
    @Size(max = 500, message = "备注长度不能超过500")
    private String remark;
}
