package com.omes.dgw.portal;

/**
 * 门户部署模式（{@code app.portal.mode}）。
 * <ul>
 *   <li>{@link #BOX}：边缘盒子 — 协议采集 + Caffeine 实时，不写 Influx</li>
 *   <li>{@link #CENTER}：中控电脑 — 协议采集 + Redis 实时 + Influx 历史</li>
 * </ul>
 */
public enum PortalMode {
    BOX,
    CENTER;

    public static PortalMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BOX;
        }
        return switch (raw.trim().toLowerCase()) {
            case "center", "edge" -> CENTER; // edge=旧配置别名
            case "box", "memory" -> BOX;     // memory=旧配置别名
            default -> BOX;
        };
    }

    public boolean isBox() {
        return this == BOX;
    }

    public boolean isCenter() {
        return this == CENTER;
    }
}
