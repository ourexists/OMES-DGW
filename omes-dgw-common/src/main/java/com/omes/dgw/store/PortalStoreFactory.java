package com.omes.dgw.store;

import com.omes.dgw.portal.PortalMode;

/**
 * Abstract Factory：按门户模式描述「缓存 / 历史 / 指标」存储栈的装配约定。
 * <p>
 * 具体装配由各 store 模块的 Spring {@code @Configuration} / AutoConfiguration 完成：
 * <ul>
 *   <li>BOX → {@code omes-dgw-store-memory}（Caffeine + Noop 历史）</li>
 *   <li>CENTER → {@code omes-dgw-store-redis} + {@code omes-dgw-store-influx}</li>
 * </ul>
 */
public interface PortalStoreFactory {

    PortalMode mode();
}
