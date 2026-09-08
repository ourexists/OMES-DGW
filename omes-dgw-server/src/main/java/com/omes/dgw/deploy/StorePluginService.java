package com.omes.dgw.deploy;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.config.AppProperties;
import com.omes.dgw.deploy.dto.PluginProbeResultVo;
import com.omes.dgw.deploy.dto.StorePluginConfigRequest;
import com.omes.dgw.deploy.dto.StorePluginStatusVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StorePluginService {

    public static final String TYPE_REDIS = "redis";
    public static final String TYPE_INFLUX = "influx";

    private static final String REDIS_MARKER = "omes-dgw-plugin-redis";
    private static final String INFLUX_MARKER = "omes-dgw-plugin-influx";
    private final DeployPaths deployPaths;
    private final AppProperties appProperties;
    private final com.omes.dgw.plugin.StorePluginRuntime runtime;

    public StorePluginService(DeployPaths deployPaths, AppProperties appProperties, com.omes.dgw.plugin.StorePluginRuntime runtime) {
        this.deployPaths = deployPaths;
        this.appProperties = appProperties;
        this.runtime = runtime;
    }

    public StorePluginStatusVo status() {
        try {
            Files.createDirectories(deployPaths.pluginsDir());
            Map<String, String> props = ExternalPropertiesFile.read(deployPaths.configFile());
            return StorePluginStatusVo.builder()
                    .pluginsDir(deployPaths.pluginsDir().toString())
                    .configFile(deployPaths.configFile().toString())
                    .redis(buildRedisSection(props))
                    .influx(buildInfluxSection(props))
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取插件状态失败: " + e.getMessage());
        }
    }

    public StorePluginStatusVo uploadPlugin(String type, MultipartFile file) {
        String pluginType = normalizeType(type);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择插件 jar 文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String name = Path.of(original).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new BusinessException("仅支持 .jar 文件");
        }
        String marker = markerFor(pluginType);
        if (!name.startsWith(marker + "-")) {
            throw new BusinessException("文件名需包含 " + marker + "（推荐 *-plugin.jar）");
        }

        try {
            Files.createDirectories(deployPaths.pluginsDir());
            Path target = deployPaths.pluginsDir().resolve(name).normalize();
            if (!target.startsWith(deployPaths.pluginsDir())) {
                throw new BusinessException("非法文件路径");
            }
            Path staged = Files.createTempFile(deployPaths.pluginsDir(), ".upload-", ".tmp");
            try {
                try (InputStream in = file.getInputStream()) {
                    Files.copy(in, staged, StandardCopyOption.REPLACE_EXISTING);
                }
                runtime.install(pluginType, staged, target);
            } finally { Files.deleteIfExists(staged); }
            return status();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("保存插件失败: " + e.getMessage());
        }
    }

    public StorePluginStatusVo removePlugin(String type) {
        String pluginType = normalizeType(type);
        try {
            runtime.remove(pluginType);
            return status();
        } catch (IOException e) {
            throw new BusinessException("删除插件失败: " + e.getMessage());
        }
    }

    public StorePluginStatusVo saveConfig(StorePluginConfigRequest request) {
        String section = StringUtils.hasText(request.getSection())
                ? request.getSection().trim().toLowerCase(Locale.ROOT)
                : "both";
        boolean saveRedis = "redis".equals(section) || "both".equals(section);
        boolean saveInflux = "influx".equals(section) || "both".equals(section);

        try {
            Map<String, String> props = ExternalPropertiesFile.read(deployPaths.configFile());
            Map<String, String> updates = new LinkedHashMap<>();

            if (saveRedis) {
                applyRedisUpdates(request, props, updates);
            }
            if (saveInflux) {
                applyInfluxUpdates(request, props, updates);
            }
            if (updates.isEmpty()) {
                throw new BusinessException("没有需要保存的配置项");
            }
            runtime.apply(updates, () -> ExternalPropertiesFile.upsert(deployPaths.configFile(), updates));
            return status();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("保存配置失败: " + e.getMessage());
        }
    }

    public PluginProbeResultVo probeRedis(String host, Integer port) {
        String h = StringUtils.hasText(host) ? host.trim() : "localhost";
        int p = port != null ? port : 6379;
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(h, p), 3000);
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return PluginProbeResultVo.builder()
                    .ok(true)
                    .message("TCP 连通 " + h + ":" + p)
                    .latencyMs(ms)
                    .build();
        } catch (IOException e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return PluginProbeResultVo.builder()
                    .ok(false)
                    .message("无法连接 " + h + ":" + p + " — " + e.getMessage())
                    .latencyMs(ms)
                    .build();
        }
    }

    /**
     * 对 Influx URL 做简单 HTTP 探测（health 或根路径），不校验 token。
     */
    public PluginProbeResultVo probeInflux(String url) {
        String raw = StringUtils.hasText(url) ? url.trim() : "http://localhost:8086";
        long start = System.nanoTime();
        try {
            URI base = URI.create(raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw);
            URL health = base.resolve("/health").toURL();
            HttpURLConnection conn = (HttpURLConnection) health.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            long ms = (System.nanoTime() - start) / 1_000_000L;
            boolean ok = code >= 200 && code < 500;
            return PluginProbeResultVo.builder()
                    .ok(ok)
                    .message(ok
                            ? "HTTP 可达 " + health + "（status=" + code + "）"
                            : "HTTP 响应异常 " + health + "（status=" + code + "）")
                    .latencyMs(ms)
                    .build();
        } catch (Exception e) {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            return PluginProbeResultVo.builder()
                    .ok(false)
                    .message("无法访问 " + raw + " — " + e.getMessage())
                    .latencyMs(ms)
                    .build();
        }
    }

    private void applyRedisUpdates(
            StorePluginConfigRequest request,
            Map<String, String> props,
            Map<String, String> updates
    ) {
        String backend = normalizeBackend(
                StringUtils.hasText(request.getStoreBackend())
                        ? request.getStoreBackend()
                        : props.getOrDefault("COLLECT_STORE_BACKEND", "caffeine"));
        if ("redis".equals(backend) && listPluginJars(TYPE_REDIS).isEmpty()
                && !runtime.loaded(TYPE_REDIS)) {
            throw new BusinessException("启用 Redis 前请先导入 omes-dgw-plugin-redis 插件 jar");
        }
        updates.put("COLLECT_STORE_BACKEND", backend);

        String host = StringUtils.hasText(request.getRedisHost())
                ? request.getRedisHost().trim()
                : props.getOrDefault("REDIS_HOST", "localhost");
        updates.put("REDIS_HOST", host);

        int port = request.getRedisPort() != null
                ? request.getRedisPort()
                : parseInt(props.get("REDIS_PORT"), 6379);
        updates.put("REDIS_PORT", String.valueOf(port));

        if (request.getRedisPassword() != null) {
            updates.put("REDIS_PASSWORD", request.getRedisPassword());
        }

        int db = request.getRedisDatabase() != null
                ? request.getRedisDatabase()
                : parseInt(props.get("REDIS_DATABASE"), 0);
        updates.put("REDIS_DATABASE", String.valueOf(db));

        if (request.getRedisKeyPrefix() != null) {
            String prefix = request.getRedisKeyPrefix().trim();
            updates.put("COLLECT_REDIS_KEY_PREFIX",
                    prefix.isEmpty() ? "collect:device:realtime" : prefix);
        }
    }

    private void applyInfluxUpdates(
            StorePluginConfigRequest request,
            Map<String, String> props,
            Map<String, String> updates
    ) {
        boolean enabled = request.getInfluxEnabled() != null
                ? request.getInfluxEnabled()
                : "influxdb".equalsIgnoreCase(props.getOrDefault("COLLECT_MONITOR_TYPE", "none"));
        if (enabled && listPluginJars(TYPE_INFLUX).isEmpty()
                && !runtime.loaded(TYPE_INFLUX)) {
            throw new BusinessException("启用 Influx 前请先导入 omes-dgw-plugin-influx 插件 jar");
        }
        updates.put("COLLECT_MONITOR_TYPE", enabled ? "influxdb" : "none");

        String url = StringUtils.hasText(request.getInfluxUrl())
                ? request.getInfluxUrl().trim()
                : props.getOrDefault("INFLUX_URL", "http://localhost:8086");
        updates.put("INFLUX_URL", url);

        if (request.getInfluxToken() != null) {
            updates.put("INFLUX_TOKEN", request.getInfluxToken());
        }

        String org = StringUtils.hasText(request.getInfluxOrg())
                ? request.getInfluxOrg().trim()
                : props.getOrDefault("INFLUX_ORG", "omes");
        updates.put("INFLUX_ORG", org);

        String bucket = StringUtils.hasText(request.getInfluxBucket())
                ? request.getInfluxBucket().trim()
                : props.getOrDefault("INFLUX_BUCKET", "dgw_metrics");
        updates.put("INFLUX_BUCKET", bucket);

        if (request.getHistoryEnabled() != null) {
            updates.put("COLLECT_HISTORY_ENABLED", String.valueOf(request.getHistoryEnabled()));
        }
        if (request.getPersistMetrics() != null) {
            updates.put("METRICS_PERSIST_INFLUX", String.valueOf(request.getPersistMetrics()));
        }
    }

    private StorePluginStatusVo.RedisSection buildRedisSection(Map<String, String> props) {
        List<String> jars = listPluginJarNames(TYPE_REDIS);
        boolean onCp = runtime.loaded(TYPE_REDIS);
        boolean jarPresent = !jars.isEmpty();
        String active = normalizeBackend(appProperties.getCollect().getStoreBackend());
        String configured = normalizeBackend(props.getOrDefault("COLLECT_STORE_BACKEND", active));

        boolean restartRequired = false;
        String hint = onCp ? "Redis 插件运行中，可在线更新或停用。" : jarPresent ? "插件已安装，选择 Redis 后保存即可在线启用。" : "当前使用本地 Caffeine，可上传 Redis 插件。";

        return StorePluginStatusVo.RedisSection.builder()
                .activeStoreBackend(active)
                .configuredStoreBackend(configured)
                .loaded(onCp)
                .jarPresent(jarPresent)
                .jarNames(jars)
                .restartRequired(restartRequired)
                .redisHost(props.getOrDefault("REDIS_HOST", "localhost"))
                .redisPort(parseInt(props.get("REDIS_PORT"), 6379))
                .redisPasswordSet(StringUtils.hasText(props.get("REDIS_PASSWORD")))
                .redisDatabase(parseInt(props.get("REDIS_DATABASE"), 0))
                .redisKeyPrefix(props.getOrDefault("COLLECT_REDIS_KEY_PREFIX", "collect:device:realtime"))
                .hint(hint)
                .build();
    }

    private StorePluginStatusVo.InfluxSection buildInfluxSection(Map<String, String> props) {
        List<String> jars = listPluginJarNames(TYPE_INFLUX);
        boolean onCp = runtime.loaded(TYPE_INFLUX);
        boolean jarPresent = !jars.isEmpty();
        boolean activeEnabled = "influxdb".equalsIgnoreCase(appProperties.getCollect().getMonitorType());
        boolean configuredEnabled = "influxdb".equalsIgnoreCase(props.getOrDefault("COLLECT_MONITOR_TYPE", appProperties.getCollect().getMonitorType()));

        boolean restartRequired = false;
        String hint = onCp ? "Influx 插件运行中，可在线更新或停用。" : jarPresent ? "插件已安装，配置并保存即可在线启用。" : "尚未安装 Influx 插件。";

        return StorePluginStatusVo.InfluxSection.builder()
                .enabled(activeEnabled)
                .configuredEnabled(configuredEnabled)
                .url(props.getOrDefault("INFLUX_URL", "http://localhost:8086"))
                .org(props.getOrDefault("INFLUX_ORG", "omes"))
                .bucket(props.getOrDefault("INFLUX_BUCKET", "dgw_metrics"))
                .tokenSet(StringUtils.hasText(props.get("INFLUX_TOKEN")))
                .historyEnabled(parseBoolean(props.get("COLLECT_HISTORY_ENABLED"),
                        appProperties.getCollect().getHistory().isEnabled()))
                .persistMetrics(parseBoolean(props.get("METRICS_PERSIST_INFLUX"),
                        appProperties.getCollect().getMetrics().isPersistToInflux()))
                .loaded(onCp)
                .jarPresent(jarPresent)
                .jarNames(jars)
                .restartRequired(restartRequired)
                .hint(hint)
                .build();
    }

    private List<String> listPluginJarNames(String type) {
        return listPluginJars(type).stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.toList());
    }

    private List<Path> listPluginJars(String type) {
        String marker = markerFor(type);
        Path dir = deployPaths.pluginsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jar") && n.contains(marker);
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new BusinessException("读取 plugins 目录失败: " + e.getMessage());
        }
    }

    private static String markerFor(String type) {
        return TYPE_INFLUX.equals(type) ? INFLUX_MARKER : REDIS_MARKER;
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BusinessException("插件类型不能为空，可选 redis 或 influx");
        }
        String v = type.trim().toLowerCase(Locale.ROOT);
        if (!TYPE_REDIS.equals(v) && !TYPE_INFLUX.equals(v)) {
            throw new BusinessException("插件类型只能是 redis 或 influx");
        }
        return v;
    }

    private static String normalizeBackend(String value) {
        if (!StringUtils.hasText(value)) {
            return "caffeine";
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return "redis".equals(v) ? "redis" : "caffeine";
    }

    private static int parseInt(String raw, int defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim());
    }
}
