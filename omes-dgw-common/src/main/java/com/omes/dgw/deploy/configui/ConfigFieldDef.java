package com.omes.dgw.deploy.configui;

import java.util.List;

/**
 * 网页配置分区：内置模块（非 jar 插件）与插件扩展字段共用同一套字段元数据。
 */
public record ConfigFieldDef(
        String key,
        String label,
        FieldType type,
        String defaultValue,
        String hint,
        List<String> options,
        boolean secret,
        /** 分区内同类字段块标题；同标题连续字段为一组 */
        String groupLabel
) {
    public enum FieldType {
        STRING, INT, DOUBLE, BOOLEAN, SELECT, PASSWORD
    }

    public ConfigFieldDef withGroup(String groupLabel) {
        return new ConfigFieldDef(key, label, type, defaultValue, hint, options, secret, groupLabel);
    }

    public static ConfigFieldDef string(String key, String label, String defaultValue, String hint) {
        return new ConfigFieldDef(key, label, FieldType.STRING, defaultValue, hint, List.of(), false, null);
    }

    public static ConfigFieldDef password(String key, String label, String hint) {
        return new ConfigFieldDef(key, label, FieldType.PASSWORD, "", hint, List.of(), true, null);
    }

    public static ConfigFieldDef integer(String key, String label, String defaultValue, String hint) {
        return new ConfigFieldDef(key, label, FieldType.INT, defaultValue, hint, List.of(), false, null);
    }

    public static ConfigFieldDef decimal(String key, String label, String defaultValue, String hint) {
        return new ConfigFieldDef(key, label, FieldType.DOUBLE, defaultValue, hint, List.of(), false, null);
    }

    public static ConfigFieldDef bool(String key, String label, String defaultValue, String hint) {
        return new ConfigFieldDef(key, label, FieldType.BOOLEAN, defaultValue, hint, List.of(), false, null);
    }

    public static ConfigFieldDef select(String key, String label, String defaultValue, List<String> options, String hint) {
        return new ConfigFieldDef(key, label, FieldType.SELECT, defaultValue, hint, options, false, null);
    }
}
