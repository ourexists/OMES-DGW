package com.omes.dgw.deploy.configui.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "保存某一配置分区")
public class RuntimeConfigSaveRequest {

    @NotEmpty
    @Schema(description = "字段键值；密钥字段传 null 表示不修改，传空串表示清空")
    private Map<String, String> values;
}
