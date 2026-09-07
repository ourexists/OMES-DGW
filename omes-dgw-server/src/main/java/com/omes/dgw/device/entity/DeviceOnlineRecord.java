package com.omes.dgw.device.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omes.dgw.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 设备在线/离线时段记录，结构对齐 {@code t_equip_record_online}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_DEVICE_ONLINE_RECORD")
public class DeviceOnlineRecord extends BaseEntity {

    /** 设备编码（对应 sn） */
    private String sn;

    @TableField("START_TIME")
    private LocalDateTime startTime;

    @TableField("END_TIME")
    private LocalDateTime endTime;

    /** 1 在线，0 离线 */
    private Integer state;

    /** 业务事件 ID，唯一；闭合时段时按此字段更新 end_time */
    @TableField("EVENT_ID")
    private String eventId;
}
