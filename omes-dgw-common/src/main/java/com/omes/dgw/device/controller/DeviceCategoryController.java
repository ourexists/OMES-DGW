package com.omes.dgw.device.controller;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.common.IdRequest;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.common.Result;
import com.omes.dgw.device.dto.DeviceCategoryDto;
import com.omes.dgw.device.dto.DeviceCategoryQuery;
import com.omes.dgw.device.dto.DeviceCategoryVo;
import com.omes.dgw.device.service.DeviceCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "设备分类")
@RestController
@RequestMapping("/device/categories")
public class DeviceCategoryController {

    private final DeviceCategoryService deviceCategoryService;

    public DeviceCategoryController(DeviceCategoryService deviceCategoryService) {
        this.deviceCategoryService = deviceCategoryService;
    }

    @Operation(summary = "分页查询")
    @PostMapping("/page")
    public Result<PageResult<DeviceCategoryVo>> page(@RequestBody DeviceCategoryQuery query) {
        return Result.ok(deviceCategoryService.pageQuery(query));
    }

    @Operation(summary = "全量列表（下拉选用）")
    @GetMapping("/all")
    public Result<List<DeviceCategoryVo>> listAll() {
        return Result.ok(deviceCategoryService.listAll());
    }

    @Operation(summary = "详情")
    @GetMapping("/detail")
    public Result<DeviceCategoryVo> detail(@RequestParam String id) {
        return Result.ok(deviceCategoryService.getDetail(id));
    }

    @Operation(summary = "新增")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/create")
    public Result<String> create(@Valid @RequestBody DeviceCategoryDto dto) {
        return Result.ok(deviceCategoryService.create(dto));
    }

    @Operation(summary = "修改")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @PostMapping("/update")
    public Result<Void> update(@Valid @RequestBody DeviceCategoryDto dto) {
        if (!StringUtils.hasText(dto.getId())) {
            throw new BusinessException("ID不能为空");
        }
        deviceCategoryService.update(dto.getId(), dto);
        return Result.ok();
    }

    @Operation(summary = "删除")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody IdRequest request) {
        deviceCategoryService.remove(request.getId());
        return Result.ok();
    }
}
