package com.omes.dgw.deploy.configui;

import java.util.List;

public record ConfigSectionDef(
        String id,
        String title,
        String description,
        /** builtin=侧栏主分区；redis / influx=嵌套连接配置 */
        String group,
        List<ConfigFieldDef> fields
) {
}
