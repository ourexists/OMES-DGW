package com.omes.dgw.deploy.configui;

import java.util.List;

public record ConfigSectionDef(
        String id,
        String title,
        String description,
        /** builtin=系统配置；plugin-redis / plugin-influx=插件二级窗口 */
        String group,
        List<ConfigFieldDef> fields
) {
}
