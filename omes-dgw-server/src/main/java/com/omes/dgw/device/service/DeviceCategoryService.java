package com.omes.dgw.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.dto.DeviceCategoryDto;
import com.omes.dgw.device.dto.DeviceCategoryQuery;
import com.omes.dgw.device.dto.DeviceCategoryVo;
import com.omes.dgw.device.entity.DeviceCategory;

import java.util.List;

public interface DeviceCategoryService extends IService<DeviceCategory> {

    PageResult<DeviceCategoryVo> pageQuery(DeviceCategoryQuery query);

    List<DeviceCategoryVo> listAll();

    DeviceCategoryVo getDetail(String id);

    String create(DeviceCategoryDto dto);

    void update(String id, DeviceCategoryDto dto);

    void remove(String id);
}
