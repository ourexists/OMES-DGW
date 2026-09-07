package com.omes.dgw.device.dto;

import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DeviceCollectConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "设备信息")
@Data
public class DeviceVo {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "设备名称")
    private String name;

    @Schema(description = "设备编码")
    private String code;

    @Schema(description = "通信协议")
    private DeviceProtocol protocol;

    @Schema(description = "协议显示名称")
    private String protocolLabel;

    @Schema(description = "所属分组编号")
    private String categoryCode;

    @Schema(description = "所属分组名称")
    private String categoryName;

    @Schema(description = "IP 地址")
    private String ipAddress;

    @Schema(description = "端口号")
    private Integer port;

    @Schema(description = "默认采集周期（秒）")
    private Integer collectIntervalSec;

    @Schema(description = "连接超时时间（毫秒）")
    private Integer connectTimeoutMs;

    @Schema(description = "PLC4X 连接串（Modbus TCP），如 modbus-tcp://127.0.0.1:502?request-timeout=4000")
    private String connectionUrl;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "采集配置（含点位列表）")
    private DeviceCollectConfig collectConfig;

    @Schema(description = "协议站号/从站号（Modbus 从站、FINS 节点、SLMP 站号）")
    private Integer unitId;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "设备实时状态；仅分页查询 includeRealtime=true 时填充，无缓存时为 null")
    private DeviceRealtimeObj realtime;
}
