package com.omes.dgw.device.online;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 在线态时段本地队列消息。
 */
@Data
public class DeviceOnlineEventQueueItem {

    private DeviceOnlineEventOp op;

    /** 主键，插入时与 eventId 一致 */
    private String id;

    /** 唯一业务键，闭合时段时用于 UPDATE */
    private String eventId;

    /** 设备编码 */
    private String sn;

    private String deviceId;

    /** 1 在线，0 离线 */
    private Integer state;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
