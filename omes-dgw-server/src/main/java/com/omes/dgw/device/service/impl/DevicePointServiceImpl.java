package com.omes.dgw.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import com.omes.dgw.device.dto.DevicePointCopyRequest;
import com.omes.dgw.device.dto.DevicePointDto;
import com.omes.dgw.device.dto.ProtocolOptionVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.service.DevicePointService;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DevicePointServiceImpl implements DevicePointService {

    private final DeviceMapper deviceMapper;
    private final ObjectMapper objectMapper;
    private final DeviceLifecycleManager lifecycleManager;

    public DevicePointServiceImpl(
            DeviceMapper deviceMapper,
            ObjectMapper objectMapper,
            DeviceLifecycleManager lifecycleManager) {
        this.deviceMapper = deviceMapper;
        this.objectMapper = objectMapper;
        this.lifecycleManager = lifecycleManager;
    }

    @Override
    public List<DevicePointConfigItem> listByDeviceId(String deviceId) {
        Device device = requireDevice(deviceId);
        if (device.getCollectConfig() == null) {
            return new ArrayList<>();
        }
        List<DevicePointConfigItem> points = DevicePointConfigSupport.normalize(device.getCollectConfig()).getPoints();
        for (DevicePointConfigItem item : points) {
            DevicePointConfigSupport.enrichDataTypeDisplay(item);
        }
        return points;
    }

    @Override
    public void save(DevicePointDto dto) {
        if (!StringUtils.hasText(dto.getDeviceId())) {
            throw new BusinessException("所属设备不能为空");
        }
        Device device = requireDevice(dto.getDeviceId());
        if (device.getProtocol() == null) {
            throw new BusinessException("设备未配置通信协议");
        }
        List<DevicePointConfigItem> items = dto.getItems() != null ? dto.getItems() : List.of();
        DevicePointConfigSupport.preparePoints(items, device.getProtocol());

        DeviceCollectConfig config = device.getCollectConfig() != null
                ? device.getCollectConfig()
                : DevicePointConfigSupport.emptyConfig();
        config.setPoints(items);
        String collectConfigJson = DevicePointConfigSupport.toJson(objectMapper, config);
        deviceMapper.update(new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, dto.getDeviceId())
                .setSql("COLLECT_CONFIG = {0}", collectConfigJson));
        device.setCollectConfig(config);
        lifecycleManager.onCollectConfigChanged(dto.getDeviceId());
    }

    @Override
    public int copy(DevicePointCopyRequest request) {
        if (request == null) {
            throw new BusinessException("请求不能为空");
        }
        if (!StringUtils.hasText(request.getSourceDeviceId())) {
            throw new BusinessException("源设备不能为空");
        }
        if (!StringUtils.hasText(request.getTargetDeviceId())) {
            throw new BusinessException("目标设备不能为空");
        }
        if (request.getSourceDeviceId().equals(request.getTargetDeviceId())) {
            throw new BusinessException("源设备与目标设备不能相同");
        }

        Device source = requireDevice(request.getSourceDeviceId());
        Device target = requireDevice(request.getTargetDeviceId());
        if (Boolean.TRUE.equals(target.getEnabled())) {
            throw new BusinessException("目标设备启用中，请先停止后再复制点位");
        }
        if (source.getProtocol() == null) {
            throw new BusinessException("源设备未配置通信协议");
        }
        if (target.getProtocol() == null) {
            throw new BusinessException("目标设备未配置通信协议");
        }
        if (source.getProtocol() != target.getProtocol()) {
            throw new BusinessException("仅支持向同协议设备复制点位（源："
                    + source.getProtocol().getLabel() + "，目标："
                    + target.getProtocol().getLabel() + "）");
        }

        DeviceCollectConfig sourceConfig = DevicePointConfigSupport.normalize(source.getCollectConfig());
        List<DevicePointConfigItem> copiedPoints = new ArrayList<>();
        for (DevicePointConfigItem item : sourceConfig.getPoints()) {
            if (item == null) {
                continue;
            }
            DevicePointConfigItem copy = objectMapper.convertValue(item, DevicePointConfigItem.class);
            copy.setDataTypeLabel(null);
            copy.setDataTypeDescription(null);
            copiedPoints.add(copy);
        }
        DevicePointConfigSupport.preparePoints(copiedPoints, target.getProtocol());

        DeviceCollectConfig targetConfig = target.getCollectConfig() != null
                ? target.getCollectConfig()
                : DevicePointConfigSupport.emptyConfig();
        targetConfig.setPoints(copiedPoints);
        if (targetConfig.getModbusByteOrder() == null) {
            targetConfig.setModbusByteOrder(sourceConfig.getModbusByteOrder());
        }

        String collectConfigJson = DevicePointConfigSupport.toJson(objectMapper, targetConfig);
        deviceMapper.update(new LambdaUpdateWrapper<Device>()
                .eq(Device::getId, target.getId())
                .setSql("COLLECT_CONFIG = {0}", collectConfigJson));
        target.setCollectConfig(targetConfig);
        lifecycleManager.onCollectConfigChanged(target.getId());
        return copiedPoints.size();
    }

    @Override
    public List<ProtocolOptionVo> listDataTypes(String deviceId, DeviceProtocol protocol) {
        DeviceProtocol resolved = resolveProtocol(deviceId, protocol);
        return PointDataType.forProtocol(resolved).stream()
                .map(ProtocolOptionVo::fromDataType)
                .toList();
    }

    @Override
    public List<ProtocolOptionVo> listAccessTypes() {
        return PointAccessType.all().stream()
                .map(t -> new ProtocolOptionVo(t.getCode(), t.getLabel(), true))
                .toList();
    }

    @Override
    public List<ProtocolOptionVo> listByteOrders(String deviceId, DeviceProtocol protocol) {
        DeviceProtocol resolved = resolveProtocol(deviceId, protocol);
        if (resolved != DeviceProtocol.MODBUS_TCP
                && resolved != DeviceProtocol.FINS_TCP
                && resolved != DeviceProtocol.SLMP_TCP) {
            return List.of();
        }
        return ModbusByteOrder.all().stream()
                .map(o -> new ProtocolOptionVo(o.getCode(), o.getLabel(), true))
                .toList();
    }

    private Device requireDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException("设备ID不能为空");
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return device;
    }

    private DeviceProtocol resolveProtocol(String deviceId, DeviceProtocol protocol) {
        if (StringUtils.hasText(deviceId)) {
            Device device = requireDevice(deviceId);
            if (device.getProtocol() == null) {
                throw new BusinessException("设备未配置通信协议");
            }
            return device.getProtocol();
        }
        if (protocol == null) {
            throw new BusinessException("请指定设备ID或协议");
        }
        return protocol;
    }
}
