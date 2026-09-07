package com.omes.dgw.device.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.omes.dgw.common.BaseEntity;
import com.omes.dgw.device.dto.DeviceCategoryDto;
import com.omes.dgw.device.dto.DeviceCategoryVo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;

import java.util.Collections;
import java.util.List;

/**
 * 设备分类（单层级，无父子关系）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_DEVICE_CATEGORY")
public class DeviceCategory extends BaseEntity {

    /** 名称 */
    private String name;

    /** 编号 */
    private String code;

    public DeviceCategoryVo toVo() {
        DeviceCategoryVo vo = new DeviceCategoryVo();
        BeanUtils.copyProperties(this, vo);
        return vo;
    }

    public static List<DeviceCategoryVo> toVoList(List<DeviceCategory> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(DeviceCategory::toVo).toList();
    }

    public static DeviceCategory fromDto(DeviceCategoryDto dto) {
        DeviceCategory entity = new DeviceCategory();
        entity.applyDto(dto);
        entity.setId(null);
        return entity;
    }

    public void applyDto(DeviceCategoryDto dto) {
        BeanUtils.copyProperties(dto, this);
    }
}
