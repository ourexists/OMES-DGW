package com.omes.dgw.store.center;

import com.omes.dgw.portal.PortalMode;
import com.omes.dgw.store.PortalStoreFactory;

/** CENTER（中控）门户 Abstract Factory 标记实现（Redis + Influx）。 */
public final class CenterPortalStoreFactory implements PortalStoreFactory {

    public static final CenterPortalStoreFactory INSTANCE = new CenterPortalStoreFactory();

    private CenterPortalStoreFactory() {
    }

    @Override
    public PortalMode mode() {
        return PortalMode.CENTER;
    }
}
