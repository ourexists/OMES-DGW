package com.omes.dgw.deploy.configui.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "配置分区（含当前值）")
public class RuntimeConfigSectionVo {
    private String id;
    private String title;
    private String description;
    private String group;
    private List<FieldVo> fields;
    private Map<String, String> values;
    /** 密钥类字段是否已设置（不回传明文） */
    private Map<String, Boolean> secretsSet;

    @Data
    @Builder
    public static class FieldVo {
        private String key;
        private String label;
        private String type;
        private String defaultValue;
        private String hint;
        private List<String> options;
        private boolean secret;
        /** 分区内同类字段块标题 */
        private String groupLabel;
    }
}
