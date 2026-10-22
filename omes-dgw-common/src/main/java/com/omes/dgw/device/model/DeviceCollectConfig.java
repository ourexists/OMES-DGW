package com.omes.dgw.device.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "设备采集配置（JSON 存入 T_DEVICE.COLLECT_CONFIG）")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceCollectConfig {

    @Schema(description = "Modbus 多寄存器默认字节序（未配置点位字节序时生效）")
    private ModbusByteOrder modbusByteOrder;

    @Schema(description = "FINS/SLMP 控制写入是否复用轮询连接；false 为每次独立短连接。Modbus 固定一条连接，忽略该字段")
    private Boolean reusePollingConnection;

    @Schema(description = "FINS 本机节点号 SA1（0 表示由驱动默认）")
    private Integer finsLocalNode;

    @Schema(description = "FINS 目标 PLC 节点号 DA1（未配置时握手自动发现，勿填 IP 末段）")
    private Integer finsRemoteNode;

    @Schema(description = "SLMP 网络号（默认 0）")
    private Integer slmpNetworkNo;

    @Schema(description = "SLMP 站号/PC 号（默认 255/0xFF 表示本站）")
    private Integer slmpStationNo;

    @Schema(description = "S7 机架号（默认 0）")
    private Integer s7Rack;

    @Schema(description = "S7 槽号（默认 1；S7-300 多为 2，S7-400 多为 3）")
    private Integer s7Slot;

    @Schema(description = "OPC UA 是否通过发现服务解析端点，默认 true")
    private Boolean opcuaDiscovery;

    @Schema(description = "OPC UA 安全策略：NONE、Basic128Rsa15、Basic256、Basic256Sha256")
    private String opcuaSecurityPolicy;

    @Schema(description = "OPC UA 用户名，空表示匿名")
    private String opcuaUsername;

    @Schema(description = "OPC UA 密码")
    private String opcuaPassword;

    @Schema(description = "点位列表（只读配实际值，只写配设定值，读写两者都配）")
    private List<DevicePointConfigItem> points = new ArrayList<>();
}
