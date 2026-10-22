package com.omes.dgw.portal;

/**
 * 门户部署模式（{@code app.portal.mode}）。
 * <ul>
 *   <li>{@link #BOX}：边缘盒子 — 全程内存（Caffeine），不做协议采集</li>
 *   <li>{@link #CENTER}：中控电脑 — Redis + InfluxDB，含完整采集能力</li>
 * </ul>
 */
public enum PortalMode {
    BOX,
    CENTER;

    /** @deprecated 兼容旧配置，等同 {@link #BOX} */
    @Deprecated
    public static final PortalMode MEMORY = BOX;

    /** @deprecated 兼容旧配置，等同 {@link #CENTER} */
    @Deprecated
    public static final PortalMode EDGE = CENTER;

    public static PortalMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return BOX;
        }
        return switch (raw.trim().toLowerCase()) {
            case "center", "edge" -> CENTER;
            case "box", "memory" -> BOX;
            default -> BOX;
        };
    }

    public boolean isBox() {
        return this == BOX;
    }

    public boolean isCenter() {
        return this == CENTER;
    }

    /** @deprecated 使用 {@link #isBox()} */
    @Deprecated
    public boolean isMemory() {
        return isBox();
    }

    /** @deprecated 使用 {@link #isCenter()} */
    @Deprecated
    public boolean isEdge() {
        return isCenter();
    }
}
