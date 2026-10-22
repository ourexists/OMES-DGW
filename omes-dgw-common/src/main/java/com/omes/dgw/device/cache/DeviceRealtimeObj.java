package com.omes.dgw.device.cache;

import com.omes.dgw.device.enums.DeviceConnectionStatus;
import com.omes.dgw.device.enums.DeviceOfflineReason;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DeviceCollectConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "设备实时状态（本地实时缓存）")
@Data
public class DeviceRealtimeObj {

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "当前在途时段 event_id（未闭合记录；状态变化时先闭合再插入新时段）")
    private String eventId;

    @Schema(description = "设备编码")
    private String deviceCode;

    @Schema(description = "设备名称")
    private String deviceName;

    @Schema(description = "通信协议")
    private DeviceProtocol protocol;

    @Schema(description = "IP 地址")
    private String ipAddress;

    @Schema(description = "端口号")
    private Integer port;

    @Schema(description = "默认采集周期（秒）")
    private Integer collectIntervalSec;

    @Schema(description = "连接超时时间（毫秒）")
    private Integer connectTimeoutMs;

    @Schema(description = "采集配置（含点位列表）")
    private DeviceCollectConfig collectConfig;

    @Schema(description = "是否在线")
    private boolean online;

    @Schema(description = "离线原因；在线时为 null")
    private DeviceOfflineReason offlineReason;

    @Schema(description = "协议采集连接状态")
    private DeviceConnectionStatus connectionStatus;

    @Schema(description = "连接状态说明")
    private String connectionStatusLabel;

    @Schema(description = "连接状态说明信息（如熔断、协议不支持）")
    private String connectionStatusMessage;

    @Schema(description = "最后更新时间")
    private LocalDateTime lastUpdateTime;

    @Schema(description = "状态说明或异常信息")
    private String message;

    @Schema(description = "点位编码 -> 实时值（编码唯一；名称允许重复）")
    private Map<String, DevicePointRealtimeObj> points = new LinkedHashMap<>();
}
