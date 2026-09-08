package com.omes.dgw.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.deploy.DeployPaths;
import com.omes.dgw.deploy.ExternalPropertiesFile;
import com.omes.dgw.device.cache.DeviceRealtimeCacheStore;
import com.omes.dgw.device.cache.caffeine.CaffeineDeviceRealtimeCacheStore;
import com.omes.dgw.timeseries.*;
import com.omes.dgw.metrics.*;
import com.omes.dgw.plugin.api.StorePlugin;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.jar.JarFile;

/** A single gate protects request leases, configuration commits and version replacement. */
@Slf4j
@Component
public class StorePluginRuntime implements ApplicationRunner {
    private final ReentrantReadWriteLock gate = new ReentrantReadWriteLock(true);
    private final Map<String, Loaded> active = new HashMap<>();
    private final DeployPaths paths;
    private final AppProperties properties;
    private final ApplicationContext context;
    private final ObjectMapper mapper;
    private DeviceRealtimeCacheStore localCache = new CaffeineDeviceRealtimeCacheStore();
    private volatile boolean historyEnabled;
    private volatile boolean metricsEnabled;

    public StorePluginRuntime(DeployPaths paths, AppProperties properties, ApplicationContext context, ObjectMapper mapper) {
        this.paths = paths; this.properties = properties; this.context = context; this.mapper = mapper;
    }

    @FunctionalInterface public interface Commit { void run() throws IOException; }

    public <T> T read(Supplier<T> action) {
        gate.readLock().lock();
        try { return action.get(); } finally { gate.readLock().unlock(); }
    }

    public void read(Runnable action) { read(() -> { action.run(); return null; }); }
    public boolean loaded(String type) { return read(() -> active.containsKey(type)); }
    public boolean historyEnabled() { return historyEnabled; }
    public boolean metricsEnabled() { return metricsEnabled; }

    public boolean isMetricsLeader() {
        return read(() -> {
            Loaded redis = active.get("redis");
            return redis == null || ((MetricsInfluxWriteLeader) redis.services.get(MetricsInfluxWriteLeader.class)).isLeader();
        });
    }

    /** Stable beans never retain an implementation from an old class loader. */
    public <T> T proxy(String type, Class<T> contract) {
        return contract.cast(Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[]{contract}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "HotPluginProxy[" + contract.getSimpleName() + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            gate.readLock().lock();
            try {
                Loaded plugin = active.get(type);
                Object target = plugin == null ? null : plugin.services.get(contract);
                if (target == null && contract == DeviceRealtimeCacheStore.class) target = localCache;
                if (target == null) {
                    if (contract == InfluxRuntime.class && (method.getName().equals("available") || method.getName().equals("ping"))) return false;
                    throw new BusinessException("插件未启用: " + type);
                }
                try { return method.invoke(target, args); }
                catch (InvocationTargetException ex) { throw ex.getCause(); }
            } finally { gate.readLock().unlock(); }
        }));
    }

    @Override public void run(ApplicationArguments args) throws Exception {
        apply(Map.of(), () -> {});
    }

    /** Prepare every affected plugin first; failure leaves old services and persisted config intact. */
    public void apply(Map<String, String> updates, Commit commit) throws IOException {
        gate.writeLock().lock();
        Map<String, Loaded> candidates = new HashMap<>();
        Set<String> affected = new HashSet<>();
        try {
            Map<String, String> values = new LinkedHashMap<>(ExternalPropertiesFile.read(paths.configFile()));
            values.putAll(updates);
            AppProperties candidateProperties = bind(values);
            for (String type : List.of("redis", "influx")) {
                boolean enabled = type.equals("redis")
                        ? "redis".equalsIgnoreCase(candidateProperties.getCollect().getStoreBackend())
                        : "influxdb".equalsIgnoreCase(candidateProperties.getCollect().getMonitorType());
                boolean changed = updates.isEmpty() || updates.keySet().stream().anyMatch(key -> affects(type, key));
                if (!changed) continue;
                affected.add(type);
                if (enabled) candidates.put(type, prepare(type, installed(type), values, candidateProperties));
            }
            DeviceRealtimeCacheStore nextCache = candidates.containsKey("redis")
                    ? (DeviceRealtimeCacheStore) candidates.get("redis").services.get(DeviceRealtimeCacheStore.class)
                    : new CaffeineDeviceRealtimeCacheStore();
            if (affected.contains("redis")) migrateCache(nextCache);
            commit.run();
            List<Loaded> previous = new ArrayList<>();
            for (String type : affected) {
                Loaded old = active.remove(type);
                if (old != null) previous.add(old);
            }
            active.putAll(candidates); candidates.clear();
            if (affected.contains("redis")) localCache = active.containsKey("redis") ? new CaffeineDeviceRealtimeCacheStore() : nextCache;
            publishProperties(candidateProperties);
            previous.forEach(this::close);
        } finally {
            candidates.values().forEach(this::close);
            gate.writeLock().unlock();
        }
    }

    public void install(String type, Path staged, Path target) throws IOException {
        gate.writeLock().lock();
        Loaded candidate = null;
        try {
            validate(type, staged);
            if (active.containsKey(type)) {
                Map<String, String> values = ExternalPropertiesFile.read(paths.configFile());
                candidate = prepare(type, staged, values, bind(values));
                if (type.equals("redis")) migrateCache((DeviceRealtimeCacheStore) candidate.services.get(DeviceRealtimeCacheStore.class));
            }
            replaceInstallation(type, staged, target);
            if (candidate != null) {
                Loaded previous = active.put(type, candidate); candidate = null;
                close(previous);
            }
        } finally {
            if (candidate != null) close(candidate);
            gate.writeLock().unlock();
        }
    }

    private void replaceInstallation(String type, Path staged, Path target) throws IOException {
        Map<Path, Path> backups = new LinkedHashMap<>();
        try {
            try (var files = Files.list(paths.pluginsDir())) {
                for (Path old : files.filter(p -> isPluginFile(type, p)).toList()) {
                    Path backup = paths.pluginsDir().resolve(".retired-" + UUID.randomUUID() + ".tmp");
                    Files.move(old, backup, StandardCopyOption.ATOMIC_MOVE);
                    backups.put(old, backup);
                }
            }
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            for (var entry : backups.entrySet()) {
                try { Files.move(entry.getValue(), entry.getKey(), StandardCopyOption.ATOMIC_MOVE); }
                catch (IOException rollback) { ex.addSuppressed(rollback); }
            }
            throw ex;
        }
        for (Path backup : backups.values()) {
            try { Files.deleteIfExists(backup); }
            catch (IOException ex) { log.warn("旧插件安装包待清理: {}", backup); }
        }
    }

    public void remove(String type) throws IOException {
        gate.writeLock().lock();
        try {
            Map<String, String> updates = type.equals("redis")
                    ? Map.of("COLLECT_STORE_BACKEND", "caffeine") : Map.of("COLLECT_MONITOR_TYPE", "none");
            apply(updates, () -> ExternalPropertiesFile.upsert(paths.configFile(), updates));
            try (var files = Files.list(paths.pluginsDir())) {
                for (Path file : files.filter(p -> isPluginFile(type, p)).toList()) Files.delete(file);
            }
        } finally { gate.writeLock().unlock(); }
    }

    private void migrateCache(DeviceRealtimeCacheStore target) throws IOException {
        Loaded redis = active.get("redis");
        DeviceRealtimeCacheStore source = redis == null ? localCache : (DeviceRealtimeCacheStore) redis.services.get(DeviceRealtimeCacheStore.class);
        for (var snapshot : source.listAll()) target.save(snapshot, mapper.writeValueAsString(snapshot));
    }

    public static boolean affects(String type, String key) {
        return type.equals("redis") ? key.startsWith("REDIS_") || key.equals("COLLECT_STORE_BACKEND") || key.equals("COLLECT_REDIS_KEY_PREFIX")
                : key.startsWith("INFLUX_") || key.startsWith("COLLECT_HISTORY_") || key.startsWith("METRICS_INFLUX_")
                    || key.equals("COLLECT_MONITOR_TYPE") || key.equals("METRICS_PERSIST_INFLUX");
    }

    private AppProperties bind(Map<String, String> values) {
        Map<String, Object> overrides = new HashMap<>();
        Map<String, String> aliases = Map.of(
                "COLLECT_STORE_BACKEND", "collect.store-backend", "COLLECT_MONITOR_TYPE", "collect.monitor-type",
                "COLLECT_REDIS_KEY_PREFIX", "collect.redis-key-prefix", "METRICS_PERSIST_INFLUX", "collect.metrics.persist-to-influx",
                "COLLECT_HISTORY_CHART_MAX_POINTS", "collect.history.chart-max-points-per-series");
        values.forEach((key, value) -> {
            String property = aliases.get(key);
            if (property == null && key.startsWith("COLLECT_HISTORY_")) property = "collect.history." + key.substring(16).toLowerCase(Locale.ROOT).replace('_', '-');
            if (property == null && key.startsWith("METRICS_INFLUX_")) property = "collect.metrics.influx-" + key.substring(15).toLowerCase(Locale.ROOT).replace('_', '-');
            if (property == null && key.startsWith("INFLUX_")) property = "influx." + key.substring(7).toLowerCase(Locale.ROOT).replace('_', '-');
            if (property != null) overrides.put("app." + property, value);
        });
        AppProperties copy = mapper.convertValue(properties, AppProperties.class);
        new Binder(new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(overrides))
                .bind("app", Bindable.ofInstance(copy));
        if (!Set.of("caffeine", "redis").contains(copy.getCollect().getStoreBackend())
                || !Set.of("none", "influxdb").contains(copy.getCollect().getMonitorType()))
            throw new BusinessException("无效的存储后端或监控类型");
        return copy;
    }

    private void publishProperties(AppProperties next) {
        properties.getCollect().setStoreBackend(next.getCollect().getStoreBackend());
        properties.getCollect().setMonitorType(next.getCollect().getMonitorType());
        properties.getCollect().setRedisKeyPrefix(next.getCollect().getRedisKeyPrefix());
        properties.getCollect().setHistory(next.getCollect().getHistory());
        properties.getCollect().setMetrics(next.getCollect().getMetrics());
        properties.setInflux(next.getInflux());
        historyEnabled = active.containsKey("influx") && next.getCollect().getHistory().isEnabled();
        metricsEnabled = active.containsKey("influx") && next.getCollect().getMetrics().isPersistToInflux();
    }

    private Path installed(String type) throws IOException {
        Files.createDirectories(paths.pluginsDir());
        try (var files = Files.list(paths.pluginsDir())) {
            List<Path> jars = files.filter(p -> isPluginFile(type, p)).toList();
            if (jars.size() != 1) throw new BusinessException("启用 " + type + " 需要且只能有一个插件包，请先上传插件");
            return jars.getFirst();
        }
    }

    private static boolean isPluginFile(String type, Path file) {
        String name = file.getFileName().toString();
        return Files.isRegularFile(file) && name.startsWith("omes-dgw-plugin-" + type + "-") && name.endsWith(".jar");
    }

    private void validate(String type, Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            var manifest = file.getManifest();
            if (manifest == null) throw new BusinessException("插件缺少 manifest");
            var attributes = manifest.getMainAttributes();
            if (!type.equals(attributes.getValue("Plugin-Id")) || !StorePlugin.API_VERSION.equals(attributes.getValue("Plugin-Requires")))
                throw new BusinessException("插件类型或 API 版本不兼容，请重新构建插件");
            String entry = "com.omes.dgw.store." + type + "." + (type.equals("redis") ? "RedisPlugin" : "InfluxPlugin");
            if (!entry.equals(attributes.getValue("Plugin-Class")) || file.getJarEntry(entry.replace('.', '/') + ".class") == null)
                throw new BusinessException("插件入口无效");
            if (file.getJarEntry("com/omes/dgw/plugin/api/StorePlugin.class") != null || file.getJarEntry("com/omes/dgw/App.class") != null)
                throw new BusinessException("插件包不得包含主服务或公共 API 类");
        }
    }

    private Loaded prepare(String type, Path source, Map<String, String> values, AppProperties candidateProperties) throws IOException {
        validate(type, source);
        Path directory = paths.pluginsDir().resolve(".runtime");
        Files.createDirectories(directory);
        Path copy = Files.createTempFile(directory, type + "-", ".jar");
        Files.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);
        JarPluginManager manager = new JarPluginManager(directory) {
            @Override protected PluginLoader createPluginLoader() {
                return new JarPluginLoader(this) {
                    @Override public ClassLoader loadPlugin(Path path, PluginDescriptor descriptor) {
                        PluginClassLoader loader = new StorePluginClassLoader(pluginManager, descriptor,
                                StorePlugin.class.getClassLoader());
                        loader.addFile(path.toFile()); return loader;
                    }
                };
            }
        };
        manager.setSystemVersion(StorePlugin.API_VERSION);
        Loaded loaded = new Loaded(manager, copy);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            String id = manager.loadPlugin(copy);
            if (!type.equals(id)) throw new BusinessException("插件 ID 不匹配");
            loaded.plugin = (StorePlugin) manager.getPlugin(id).getPlugin();
            Thread.currentThread().setContextClassLoader(manager.getPluginClassLoader(id));
            loaded.plugin.initialize(new StorePlugin.Host() {
                @Override public <T> T service(Class<T> contract) {
                    return contract == AppProperties.class ? contract.cast(candidateProperties) : context.getBean(contract);
                }
                @Override public Map<String, String> configuration() {
                    Map<String, String> effective = new HashMap<>(values);
                    for (String key : List.of("REDIS_HOST", "REDIS_PORT", "REDIS_DATABASE", "REDIS_PASSWORD")) {
                        String value = context.getEnvironment().getProperty(key);
                        if (value != null) effective.putIfAbsent(key, value);
                    }
                    return Map.copyOf(effective);
                }
            });
            loaded.services = Map.copyOf(loaded.plugin.services());
            List<Class<?>> required = type.equals("redis")
                    ? List.of(DeviceRealtimeCacheStore.class, MetricsInfluxWriteLeader.class)
                    : List.of(InfluxRuntime.class, DeviceRealtimeHistoryWriter.class, DeviceSnapshotHistoryQuery.class,
                              ProtocolMetricsHistoryWriter.class, ProtocolMetricsHistoryQuery.class);
            for (Class<?> contract : required) {
                if (!contract.isInstance(loaded.services.get(contract))) throw new BusinessException("插件缺少接口: " + contract.getSimpleName());
            }
            loaded.plugin.checkHealth();
            if (manager.startPlugin(id) != PluginState.STARTED) throw new BusinessException("插件启动失败: " + type);
            return loaded;
        } catch (Exception | LinkageError ex) {
            close(loaded);
            throw new BusinessException("插件 " + type + " 初始化失败，原运行实例保留: " + ex.getClass().getSimpleName());
        } finally { Thread.currentThread().setContextClassLoader(previous); }
    }

    private void close(Loaded loaded) {
        try {
            // Explicit stop also handles candidates whose initialize failed before PF4J STARTED.
            if (loaded.plugin != null) loaded.plugin.stop();
        } catch (Exception ex) { log.warn("插件资源关闭失败", ex); }
        finally {
            loaded.services = Map.of(); loaded.plugin = null;
            try { loaded.manager.unloadPlugins(); } catch (Exception ex) { log.warn("插件卸载失败", ex); }
            try { Files.deleteIfExists(loaded.copy); } catch (IOException ex) { log.warn("插件临时文件待清理: {}", loaded.copy); }
        }
    }

    @PreDestroy public void shutdown() {
        gate.writeLock().lock();
        try { active.values().forEach(this::close); active.clear(); }
        finally { gate.writeLock().unlock(); }
    }

    private static final class Loaded {
        final JarPluginManager manager;
        final Path copy;
        StorePlugin plugin;
        Map<Class<?>, Object> services = Map.of();
        Loaded(JarPluginManager manager, Path copy) { this.manager = manager; this.copy = copy; }
    }
}
