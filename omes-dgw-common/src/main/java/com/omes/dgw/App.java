package com.omes.dgw;

/**
 * 原单体入口已拆为双门户，请启动：
 * <ul>
 *   <li>{@code com.omes.dgw.MemoryPortalApp}（omes-dgw-portal-memory）</li>
 *   <li>{@code com.omes.dgw.EdgePortalApp}（omes-dgw-portal-edge）</li>
 * </ul>
 */
@Deprecated(since = "1.0", forRemoval = false)
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        throw new UnsupportedOperationException(
                "请启动 omes-dgw-portal-memory（MemoryPortalApp）或 omes-dgw-portal-edge（EdgePortalApp）");
    }
}
