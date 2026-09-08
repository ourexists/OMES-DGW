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
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.mapper.DeviceCategoryMapper;
import com.omes.dgw.device.service.DeviceCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DeviceCategoryServiceImpl
        extends ServiceImpl<DeviceCategoryMapper, DeviceCategory>
        implements DeviceCategoryService {

    private final DeviceMapper deviceMapper;

    public DeviceCategoryServiceImpl(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

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
        validateCategory(dto, null);
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
        validateCategory(dto, id);
        if (!entity.getCode().equals(dto.getCode())) {
            assertNoDevices(entity.getCode(), "分组下存在设备，不能修改分组编码");
        }
        entity.applyDto(dto);
        entity.setId(id);
        updateById(entity);
    }

    @Override
    public void remove(String id) {
        DeviceCategory entity = getById(id);
        if (entity == null) {
            throw new BusinessException("设备分类不存在或已删除");
        }
        assertNoDevices(entity.getCode(), "分组下存在设备，请先调整设备所属分组后再删除");
        if (!removeById(id)) {
            throw new BusinessException("设备分类不存在或已删除");
        }
    }

    private void assertNoDevices(String code, String message) {
        if (deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getCategoryCode, code)) > 0) {
            throw new BusinessException(message);
        }
    }

    private void validateCategory(DeviceCategoryDto dto, String id) {
        if (!StringUtils.hasText(dto.getName()) || !StringUtils.hasText(dto.getCode())) {
            throw new BusinessException("分组名称和编码不能为空");
        }
        dto.setName(dto.getName().trim());
        dto.setCode(dto.getCode().trim());
        if (dto.getName().length() > 200 || dto.getCode().length() > 64) {
            throw new BusinessException("分组名称不能超过200个字符，编码不能超过64个字符");
        }
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<DeviceCategory>()
                .eq(DeviceCategory::getCode, dto.getCode())
                .ne(id != null, DeviceCategory::getId, id);
        if (count(wrapper) > 0) {
            throw new BusinessException("分组编码已存在");
        }
    }

    private LambdaQueryWrapper<DeviceCategory> buildFilter(DeviceCategoryQuery query) {
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), DeviceCategory::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), DeviceCategory::getCode, query.getCode());
        return wrapper;
    }
}
