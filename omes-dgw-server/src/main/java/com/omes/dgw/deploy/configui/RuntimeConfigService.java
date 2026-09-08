package com.omes.dgw.deploy.configui;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.deploy.DeployPaths;
import com.omes.dgw.deploy.ExternalPropertiesFile;
import com.omes.dgw.deploy.configui.dto.RuntimeConfigSaveRequest;
import com.omes.dgw.deploy.configui.dto.RuntimeConfigSectionVo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuntimeConfigService {

    private final DeployPaths deployPaths;
    private final com.omes.dgw.plugin.StorePluginRuntime plugins;

    public RuntimeConfigService(DeployPaths deployPaths, com.omes.dgw.plugin.StorePluginRuntime plugins) {
        this.deployPaths = deployPaths;
        this.plugins = plugins;
    }

    public List<RuntimeConfigSectionVo> listSections(String group) {
        return RuntimeConfigCatalog.all().stream()
                .filter(s -> group == null || group.isBlank() || s.group().equals(group))
                .map(this::toVo)
                .collect(Collectors.toList());
    }

    public RuntimeConfigSectionVo getSection(String id) {
        ConfigSectionDef def = RuntimeConfigCatalog.byId(id);
        if (def == null) {
            throw new BusinessException("未知配置分区: " + id);
        }
        return toVo(def);
    }

    public RuntimeConfigSectionVo saveSection(String id, RuntimeConfigSaveRequest request) {
        ConfigSectionDef def = RuntimeConfigCatalog.byId(id);
        if (def == null) {
            throw new BusinessException("未知配置分区: " + id);
        }
        if (request.getValues() == null || request.getValues().isEmpty()) {
            throw new BusinessException("没有需要保存的配置项");
        }

        Map<String, ConfigFieldDef> fieldMap = def.fields().stream()
                .collect(Collectors.toMap(ConfigFieldDef::key, f -> f, (a, b) -> a, LinkedHashMap::new));

        try {
            Map<String, String> current = ExternalPropertiesFile.read(deployPaths.configFile());
            Map<String, String> updates = new LinkedHashMap<>();

            for (Map.Entry<String, String> e : request.getValues().entrySet()) {
                String key = e.getKey();
                ConfigFieldDef field = fieldMap.get(key);
                if (field == null) {
                    throw new BusinessException("分区 " + id + " 不包含配置项: " + key);
                }
                String value = e.getValue();
                if (field.secret()) {
                    if (value == null) {
                        continue; // 不修改
                    }
                    updates.put(key, value);
                    continue;
                }
                if (value == null) {
                    value = field.defaultValue() != null ? field.defaultValue() : "";
                }
                updates.put(key, value.trim());
            }

            if (updates.isEmpty()) {
                throw new BusinessException("没有需要保存的配置项");
            }
            if (id.startsWith("plugin-") || updates.containsKey("COLLECT_STORE_BACKEND")) {
                plugins.apply(updates, () -> ExternalPropertiesFile.upsert(deployPaths.configFile(), updates));
            } else {
                ExternalPropertiesFile.upsert(deployPaths.configFile(), updates);
            }
            return toVo(def);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("保存配置失败: " + ex.getMessage());
        }
    }

    private RuntimeConfigSectionVo toVo(ConfigSectionDef def) {
        try {
            Map<String, String> props = ExternalPropertiesFile.read(deployPaths.configFile());
            Map<String, String> values = new LinkedHashMap<>();
            Map<String, Boolean> secretsSet = new LinkedHashMap<>();
            List<RuntimeConfigSectionVo.FieldVo> fields = new ArrayList<>();

            for (ConfigFieldDef f : def.fields()) {
                fields.add(RuntimeConfigSectionVo.FieldVo.builder()
                        .key(f.key())
                        .label(f.label())
                        .type(f.type().name().toLowerCase())
                        .defaultValue(f.defaultValue())
                        .hint(f.hint())
                        .options(f.options())
                        .secret(f.secret())
                        .groupLabel(f.groupLabel())
                        .build());

                if (f.secret()) {
                    boolean set = StringUtils.hasText(props.get(f.key()));
                    secretsSet.put(f.key(), set);
                    values.put(f.key(), ""); // 不回传明文
                } else {
                    String v = props.get(f.key());
                    if (v == null) {
                        v = f.defaultValue() != null ? f.defaultValue() : "";
                    }
                    values.put(f.key(), v);
                }
            }

            return RuntimeConfigSectionVo.builder()
                    .id(def.id())
                    .title(def.title())
                    .description(def.description())
                    .group(def.group())
                    .fields(fields)
                    .values(values)
                    .secretsSet(secretsSet)
                    .build();
        } catch (IOException e) {
            throw new BusinessException("读取配置失败: " + e.getMessage());
        }
    }
}
