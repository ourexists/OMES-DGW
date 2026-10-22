package com.omes.dgw.device.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;
import com.omes.dgw.device.dto.DeviceCopyRequest;
import com.omes.dgw.device.dto.DevicePointScanVo;
import com.omes.dgw.device.dto.DeviceDto;
import com.omes.dgw.device.dto.DeviceQuery;
import com.omes.dgw.device.dto.DeviceVo;
import com.omes.dgw.device.entity.Device;

public interface DeviceService extends IService<Device> {

    PageResult<DeviceVo> pageQuery(DeviceQuery query);

    DeviceVo getDetail(String id);

    String create(DeviceDto dto);

    /**
     * 按源设备复制一台新设备（协议与点位配置一并复制，新设备强制停用）。
     */
    String copy(DeviceCopyRequest request);

    void update(String id, DeviceDto dto);

    void remove(String id);

    DeviceConnectionTestVo testConnection(String deviceId);

    DeviceConnectionTestVo testConnection(DeviceDto dto);

    /**
     * 一次性读取设备连接下全部配置点位的地址与当前值（设备须已启用）。
     */
    DevicePointScanVo scanPoints(String deviceId);
}
