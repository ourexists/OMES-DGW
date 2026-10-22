package com.omes.dgw.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 设备通信协议
 */
@Getter
public enum DeviceProtocol {

    MODBUS_TCP("Modbus TCP", true),
    FINS_TCP("FINS TCP", true),
    SLMP_TCP("Mitsubishi SLMP", true),
    S7_TCP("Siemens S7", true),
    OPC_UA("OPC UA", true);

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    /** 平台已实现采集、连接测试等能力 */
    private final boolean supported;

    DeviceProtocol(String label, boolean supported) {
        this.code = name();
        this.label = label;
        this.supported = supported;
    }

    public boolean isSupported() {
        return supported;
    }

    public static boolean isSupported(DeviceProtocol protocol) {
        return protocol != null && protocol.supported;
    }

    @JsonCreator
    public static DeviceProtocol fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (DeviceProtocol protocol : values()) {
            if (protocol.code.equalsIgnoreCase(value) || protocol.name().equalsIgnoreCase(value)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("不支持的协议: " + value);
    }

    public static DeviceProtocol fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DeviceProtocol protocol : values()) {
            if (protocol.label.equals(trimmed)) {
                return protocol;
            }
        }
        return fromCode(trimmed);
    }
}
