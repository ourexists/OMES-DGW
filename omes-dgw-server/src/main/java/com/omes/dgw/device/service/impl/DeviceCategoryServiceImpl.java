package com.omes.dgw.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.dto.DeviceCategoryDto;
import com.omes.dgw.device.dto.DeviceCategoryQuery;
import com.omes.dgw.device.dto.DeviceCategoryVo;
import com.omes.dgw.device.entity.DeviceCategory;
import com.omes.dgw.device.mapper.DeviceCategoryMapper;
import com.omes.dgw.device.service.DeviceCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DeviceCategoryServiceImpl
        extends ServiceImpl<DeviceCategoryMapper, DeviceCategory>
        implements DeviceCategoryService {

    @Override
    public PageResult<DeviceCategoryVo> pageQuery(DeviceCategoryQuery query) {
        LambdaQueryWrapper<DeviceCategory> wrapper = buildFilter(query);
        wrapper.orderByAsc(DeviceCategory::getCode);
        Page<DeviceCategory> page = page(new Page<>(query.getPage(), query.getSize()), wrapper);
        Page<DeviceCategoryVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(DeviceCategory.toVoList(page.getRecords()));
        return PageResult.of(voPage);
    }

    @Override
    public List<DeviceCategoryVo> listAll() {
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DeviceCategory::getCode);
        return DeviceCategory.toVoList(list(wrapper));
    }

    @Override
    public DeviceCategoryVo getDetail(String id) {
        DeviceCategory entity = getById(id);
        if (entity == null) {
            throw new BusinessException("设备分类不存在");
        }
        return entity.toVo();
    }

    @Override
    public String create(DeviceCategoryDto dto) {
        DeviceCategory entity = DeviceCategory.fromDto(dto);
        save(entity);
        return entity.getId();
    }

    @Override
    public void update(String id, DeviceCategoryDto dto) {
        DeviceCategory entity = getById(id);
        if (entity == null) {
            throw new BusinessException("设备分类不存在");
        }
        entity.applyDto(dto);
        entity.setId(id);
        updateById(entity);
    }

    @Override
    public void remove(String id) {
        if (!removeById(id)) {
            throw new BusinessException("设备分类不存在或已删除");
        }
    }

    private LambdaQueryWrapper<DeviceCategory> buildFilter(DeviceCategoryQuery query) {
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), DeviceCategory::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), DeviceCategory::getCode, query.getCode());
        return wrapper;
    }
}
