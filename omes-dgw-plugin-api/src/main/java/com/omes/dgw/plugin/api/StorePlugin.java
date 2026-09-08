package com.omes.dgw.plugin.api;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;
import java.util.Map;

/** Host-owned contracts must never be bundled in a plugin jar. */
public abstract class StorePlugin extends Plugin {
    public static final String API_VERSION = "1.0.0";

    protected StorePlugin(PluginWrapper wrapper) { super(wrapper); }

    public interface Host {
        <T> T service(Class<T> type);
        Map<String, String> configuration();
    }

    /** Create resources without making the candidate visible to requests. */
    public abstract void initialize(Host host) throws Exception;
    public abstract Map<Class<?>, Object> services();
    public abstract void checkHealth() throws Exception;
    /** Must release all clients, executors and listeners, including partial initialization. */
    @Override public abstract void stop();
}
