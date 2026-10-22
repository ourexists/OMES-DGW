package com.omes.dgw.portal;

import java.nio.file.Path;
import java.util.Map;

/**
 * Dual-portal startup template: deploy root and process restart wait.
 */
public abstract class AbstractDgwPortal {

    protected AbstractDgwPortal() {
    }

    /**
     * Common prepare before start: await previous process exit, return Spring default properties
     * (includes {@code omes.deploy.root}).
     */
    protected static Map<String, Object> prepareDefaultProperties() {
        awaitPreviousProcess();
        String root = deployRoot().toString().replace('\\', '/');
        return Map.of("omes.deploy.root", root);
    }

    protected static Path deployRoot() {
        return resolveDeployRoot(Path.of(""));
    }

    /** Implemented by DeployPaths when present on classpath. */
    protected static Path resolveDeployRoot(Path base) {
        try {
            Class<?> deployPaths = Class.forName("com.omes.dgw.deploy.DeployPaths");
            return (Path) deployPaths.getMethod("rootDirectory", Path.class).invoke(null, base);
        } catch (ReflectiveOperationException e) {
            return base.toAbsolutePath().normalize();
        }
    }

    protected static void awaitPreviousProcess() {
        try {
            Class<?> restart = Class.forName("com.omes.dgw.deploy.RestartProcess");
            restart.getMethod("awaitPreviousProcess").invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // DeployPaths / RestartProcess missing on classpath
        }
    }
}