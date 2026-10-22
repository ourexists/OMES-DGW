package com.omes.dgw.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "分页结果")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码")
    private long page;

    @Schema(description = "每页条数")
    private long size;

    @Schema(description = "当前页数据列表")
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }
}
