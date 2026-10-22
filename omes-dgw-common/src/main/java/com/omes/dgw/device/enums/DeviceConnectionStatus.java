package com.omes.dgw.device.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 设备协议采集连接状态（运行时，非数据库字段）。
 */
@Getter
public enum DeviceConnectionStatus {

    /** 设备未启用（enabled=false） */
    DISABLED("disabled", "未启用"),

    /** 已启用但协议轮询未启动（含熔断等待退避） */
    DISCONNECTED("disconnected", "连接中断"),

    /** 熔断自动恢复已放弃，需人工开启连接 */
    CIRCUIT_ABANDONED("circuit_abandoned", "熔断放弃"),

    /** 熔断后半开恢复探测中（轮询已重启，尚未确认成功） */
    HALF_OPEN("half_open", "半开恢复中"),

    /** 已启用且协议轮询已启动 */
    CONNECTED("connected", "连接建立"),

    /** 已启用但当前协议不支持采集连接 */
    UNSUPPORTED("unsupported", "协议不支持");

    @JsonValue
    private final String code;

    private final String label;

    DeviceConnectionStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonCreator
    public static DeviceConnectionStatus fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeviceConnectionStatus status : values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("不支持的连接状态: " + value);
    }
}
