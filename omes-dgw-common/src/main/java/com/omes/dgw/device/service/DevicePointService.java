package com.omes.dgw.device.service;

import com.omes.dgw.device.dto.DevicePointCopyRequest;
import com.omes.dgw.device.dto.DevicePointDto;
import com.omes.dgw.device.dto.ProtocolOptionVo;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DevicePointConfigItem;

import java.util.List;

public interface DevicePointService {

    List<DevicePointConfigItem> listByDeviceId(String deviceId);

    void save(DevicePointDto dto);

    /**
     * 将源设备点位全量覆盖到目标设备（须同协议，目标设备须停用）。
     *
     * @return 复制后的点位数量
     */
    int copy(DevicePointCopyRequest request);

    List<ProtocolOptionVo> listDataTypes(String deviceId, DeviceProtocol protocol);

    List<ProtocolOptionVo> listAccessTypes();

    /**
     * Modbus 多寄存器字节序选项；非 Modbus 协议返回空列表。
     */
    List<ProtocolOptionVo> listByteOrders(String deviceId, DeviceProtocol protocol);
}
