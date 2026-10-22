package com.omes.dgw.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 控制点位写入指令状态
 */
@Getter
public enum WriteCommandStatus {

    PENDING("待执行"),
    RUNNING("执行中"),
    SUCCESS("成功"),
    FAILED("失败");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    WriteCommandStatus(String label) {
        this.code = name();
        this.label = label;
    }

    @JsonCreator
    public static WriteCommandStatus fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (WriteCommandStatus status : values()) {
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("不支持的写入指令状态: " + value);
    }
}
