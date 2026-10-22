package com.omes.dgw.device.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 点位用途分类：采集点位参与曲线；异常点位仅采集不进曲线；写入点位用于控制下发。
 */
@Getter
public enum PointKind {

    COLLECT("采集点位"),
    EXCEPTION("异常点位"),
    WRITE("写入点位");

    @EnumValue
    @JsonValue
    private final String code;

    private final String label;

    PointKind(String label) {
        this.code = name();
        this.label = label;
    }

    public static List<PointKind> all() {
        return Arrays.asList(values());
    }

    /** 参与实时采集（采集点、异常点）。 */
    public boolean isCollectable() {
        return this == COLLECT || this == EXCEPTION;
    }

    /** 参与历史曲线（仅采集点）。 */
    public boolean isChartable() {
        return this == COLLECT;
    }

    public boolean isWritable() {
        return this == WRITE;
    }

    public boolean isException() {
        return this == EXCEPTION;
    }

    @JsonCreator
    public static PointKind fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (PointKind kind : values()) {
            if (kind.code.equalsIgnoreCase(value) || kind.name().equalsIgnoreCase(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("不支持的点位分类: " + value);
    }

    public static PointKind fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (PointKind kind : values()) {
            if (kind.label.equals(trimmed)) {
                return kind;
            }
        }
        return fromCode(trimmed);
    }
}
