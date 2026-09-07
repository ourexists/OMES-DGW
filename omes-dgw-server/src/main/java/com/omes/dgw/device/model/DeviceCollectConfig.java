package com.omes.dgw.device.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
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

    @Schema(description = "Modbus 控制写入是否复用采集轮询连接池；false（默认）为每次独立短连接")
    private Boolean reusePollingConnection;

    @Schema(description = "FINS 本机节点号 SA1（0 表示由驱动默认）")
    private Integer finsLocalNode;

    @Schema(description = "FINS 目标 PLC 节点号 DA1（未配置时握手自动发现，勿填 IP 末段）")
    private Integer finsRemoteNode;

    @Schema(description = "SLMP 网络号（默认 0）")
    private Integer slmpNetworkNo;

    @Schema(description = "SLMP 站号/PC 号（默认 255/0xFF 表示本站）")
    private Integer slmpStationNo;

    @Schema(description = "点位列表（只读配实际值，只写配设定值，读写两者都配）")
    private List<DevicePointConfigItem> points = new ArrayList<>();
}
