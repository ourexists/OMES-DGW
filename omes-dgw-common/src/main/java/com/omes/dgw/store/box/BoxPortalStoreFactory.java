package com.omes.dgw.store.box;

import com.omes.dgw.portal.PortalMode;
import com.omes.dgw.store.PortalStoreFactory;

/** BOX（边缘盒子）门户 Abstract Factory 标记实现。 */
public final class BoxPortalStoreFactory implements PortalStoreFactory {

    public static final BoxPortalStoreFactory INSTANCE = new BoxPortalStoreFactory();

    private BoxPortalStoreFactory() {
    }

    @Override
    public PortalMode mode() {
        return PortalMode.BOX;
    }
}
