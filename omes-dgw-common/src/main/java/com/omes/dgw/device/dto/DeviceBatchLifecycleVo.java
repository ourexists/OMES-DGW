package com.omes.dgw.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "批量启用/停用结果")
@Data
public class DeviceBatchLifecycleVo {

    @Schema(description = "提交台数（去重后）")
    private int total;

    @Schema(description = "实际变更台数")
    private int success;

    @Schema(description = "已是目标状态而跳过的台数")
    private int skipped;

    @Schema(description = "失败台数")
    private int failed;

    @Schema(description = "结果摘要")
    private String message;

    @Schema(description = "逐台结果")
    private List<DeviceBatchLifecycleItemVo> items = new ArrayList<>();
}
