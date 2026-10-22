package com.omes.dgw.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 点位读写控制类型
 */
@Getter
public enum PointAccessType {

    READ_ONLY("只读"),
    READ_WRITE("读写"),
    WRITE_ONLY("只写");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    PointAccessType(String label) {
        this.code = name();
        this.label = label;
    }

    public static List<PointAccessType> all() {
        return Arrays.asList(values());
    }

    public boolean isWritable() {
        return this == WRITE_ONLY || this == READ_WRITE;
    }

    /** 参与实时采集（只读、读写须配置实际值点位）。 */
    public boolean isCollectable() {
        return this == READ_ONLY || this == READ_WRITE;
    }

    @JsonCreator
    public static PointAccessType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PointAccessType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的读写控制类型: " + value);
    }

    public static PointAccessType fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (PointAccessType type : values()) {
            if (type.label.equals(trimmed)) {
                return type;
            }
        }
        return fromCode(trimmed);
    }
}
