package com.omes.dgw.device.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 设备离线原因（运行时，写入实时缓存）。
 */
@Getter
public enum DeviceOfflineReason {

    /** 采集超时未更新实时缓存 */
    TIMEOUT("timeout", "采集超时"),

    /** 连接熔断（连续失败或半开探测失败） */
    CIRCUIT_BREAKER("circuit_breaker", "连接熔断"),

    /** 熔断自动恢复已放弃 */
    CIRCUIT_ABANDONED("circuit_abandoned", "熔断放弃");

    @JsonValue
    private final String code;

    private final String label;

    DeviceOfflineReason(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonCreator
    public static DeviceOfflineReason fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeviceOfflineReason reason : values()) {
            if (reason.code.equalsIgnoreCase(value) || reason.name().equalsIgnoreCase(value)) {
                return reason;
            }
        }
        throw new IllegalArgumentException("不支持的离线原因: " + value);
    }
}
