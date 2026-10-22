package com.omes.dgw.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "按 ID 操作请求体")
@Data
public class IdRequest {

    @Schema(description = "主键 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "ID不能为空")
    private String id;
}
