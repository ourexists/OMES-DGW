package com.omes.dgw.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.cache.DevicePointRealtimeObj;
import com.omes.dgw.device.cache.DeviceRealtimeCache;
import com.omes.dgw.device.cache.DeviceRealtimePointSupport;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.controller.core.DeviceLifecycleManager;
import com.omes.dgw.device.dto.*;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.entity.DeviceCategory;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.service.DeviceCategoryService;
import com.omes.dgw.device.service.DeviceService;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.protocol.DeviceProtocolConnectFactory;
import com.omes.dgw.protocol.ProtocolConnect;
import com.omes.dgw.protocol.ProtocolExecutor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Autowired
    private DeviceCategoryService deviceCategoryService;

    @Autowired
    private DeviceRealtimeCache realtimeCacheService;

    @Autowired
    private ProtocolExecutor protocolExecutor;

    @Autowired
    private DeviceLifecycleManager lifecycleManager;

    @Override
    public PageResult<DeviceVo> pageQuery(DeviceQuery query) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getName()), Device::getName, query.getName())
                .like(StringUtils.hasText(query.getCode()), Device::getCode, query.getCode())
                .eq(query.getProtocol() != null, Device::getProtocol, query.getProtocol())
                .eq(StringUtils.hasText(query.getCategoryCode()), Device::getCategoryCode, query.getCategoryCode())
                .eq(query.getEnabled() != null, Device::getEnabled, query.getEnabled());
        wrapper.orderByDesc(Device::getCreateTime);
        long pageNo = query.getPage() < 1 ? 1 : query.getPage();
        long pageSize = query.getSize() < 1 ? 10 : query.getSize();
        Page<Device> page = page(new Page<>(pageNo, pageSize), wrapper);
        Page<DeviceVo> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<Device> entities = page.getRecords();
        List<DeviceVo> records = Device.toVoList(entities, loadCategoryNameMap(entities));
        if (Boolean.TRUE.equals(query.getIncludeRealtime())) {
            fillRealtime(records);
        }
        voPage.setRecords(records);
        return PageResult.of(voPage);
    }

    @Override
    public DeviceVo getDetail(String id) {
        Device entity = getById(id);
        if (entity == null) {
            throw new BusinessException("设备不存在");
        }
        Map<String, String> categoryNames = loadCategoryNameMap(Collections.singletonList(entity));
        return entity.toVo(categoryNames.get(entity.getCategoryCode()));
    }

    @Override
    public String create(DeviceDto dto) {
        assertCategoryExists(dto.getCategoryCode());
        Device entity = Device.fromDto(dto);
        save(entity);
        return entity.getId();
    }

    @Override
    public String copy(DeviceCopyRequest request) {
        if (request == null || !StringUtils.hasText(request.getSourceDeviceId())) {
            throw new BusinessException("源设备不能为空");
        }
        if (request.getDevice() == null) {
            throw new BusinessException("新设备信息不能为空");
        }
        Device source = getById(request.getSourceDeviceId());
        if (source == null) {
            throw new BusinessException("源设备不存在");
        }
        if (source.getProtocol() == null) {
            throw new BusinessException("源设备未配置通信协议");
        }

        DeviceDto dto = request.getDevice();
        dto.setId(null);
        dto.setProtocol(source.getProtocol());
        dto.setEnabled(false);
        assertCategoryExists(dto.getCategoryCode());

        Device entity = Device.fromDto(dto);
        DeviceCollectConfig sourceConfig = DevicePointConfigSupport.normalize(source.getCollectConfig());
        DeviceCollectConfig targetConfig = entity.getCollectConfig() != null
                ? entity.getCollectConfig()
                : DevicePointConfigSupport.emptyConfig();

        List<DevicePointConfigItem> copiedPoints = copyPointItems(sourceConfig.getPoints());
        DevicePointConfigSupport.preparePoints(copiedPoints, source.getProtocol());
        targetConfig.setPoints(copiedPoints);
        if (targetConfig.getModbusByteOrder() == null) {
            targetConfig.setModbusByteOrder(sourceConfig.getModbusByteOrder());
        }
        entity.setCollectConfig(DevicePointConfigSupport.normalize(targetConfig, source.getProtocol()));
        save(entity);
        return entity.getId();
    }

    private static List<DevicePointConfigItem> copyPointItems(List<DevicePointConfigItem> source) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<>();
        }
        List<DevicePointConfigItem> result = new ArrayList<>(source.size());
        for (DevicePointConfigItem item : source) {
            if (item == null) {
                continue;
            }
            DevicePointConfigItem copy = new DevicePointConfigItem();
            BeanUtils.copyProperties(item, copy);
            copy.setDataTypeLabel(null);
            copy.setDataTypeDescription(null);
            result.add(copy);
        }
        return result;
    }

    @Override
    public void update(String id, DeviceDto dto) {
        Device entity = getById(id);
        if (entity == null) {
            throw new BusinessException("设备不存在");
        }
        assertCategoryExists(dto.getCategoryCode());
        // 启停只能走 /device/realtime/enable|stop；编辑请求常不传 enabled，
        // DeviceDto.enabled 默认 false，若照抄会把已启用设备静默改成停用且不清理 Redis。
        Boolean previousEnabled = entity.getEnabled();
        entity.applyDto(dto);
        entity.setEnabled(previousEnabled);
        entity.setId(id);
        updateById(entity);
    }

    @Override
    public void remove(String id) {
        Device entity = getById(id);
        boolean r = this.remove(new LambdaQueryWrapper<Device>()
                .eq(Device::getId, id)
                .eq(Device::getEnabled, false));
        if (!r) {
            throw new BusinessException("设备不存在或启用中,只有停用的设备可以删除");
        }
        if (entity != null) {
            lifecycleManager.onDeviceRemoved(entity);
        }
    }

    @Override
    public DeviceConnectionTestVo testConnection(String deviceId) {
        Device device = getById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        return doTestConnection(device);
    }

    @Override
    public DeviceConnectionTestVo testConnection(DeviceDto dto) {
        Device device = Device.fromDto(dto);
        if (StringUtils.hasText(dto.getId())) {
            device.setId(dto.getId());
        }
        return doTestConnection(device);
    }

    @Override
    public DevicePointScanVo scanPoints(String deviceId) {
        Device device = getById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if (!DeviceLifecycleManager.isEnabled(device)) {
            throw new BusinessException("设备未启用，请先启用设备后再扫描点位");
        }
        DeviceProtocol protocol = device.getProtocol();
        if (!DeviceProtocol.isSupported(protocol)) {
            String label = protocol != null ? protocol.getLabel() : "未知";
            throw new BusinessException("暂不支持该协议的点位扫描: " + label);
        }
        DeviceRealtimeObj cached = realtimeCacheService.getByDeviceId(deviceId);
        if (cached == null) {
            realtimeCacheService.save(device.toRealtimeVo());
        }
        long startNanos = System.nanoTime();
        DeviceRealtimeObj snapshot = protocolExecutor.readAllPoints(protocol.getCode(), deviceId);
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;
        if (snapshot == null) {
            throw new BusinessException("无法读取点位：设备缓存或连接参数不可用");
        }
        return toScanVo(device, snapshot, latencyMs);
    }

    private static DevicePointScanVo toScanVo(Device device, DeviceRealtimeObj snapshot, long latencyMs) {
        DevicePointScanVo vo = new DevicePointScanVo();
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setOnline(snapshot.isOnline());
        vo.setMessage(snapshot.getMessage());
        vo.setLatencyMs(latencyMs);
        vo.setReadTime(snapshot.getLastUpdateTime() != null ? snapshot.getLastUpdateTime() : LocalDateTime.now());
        Map<String, DevicePointRealtimeObj> realtimePoints =
                snapshot.getPoints() != null ? snapshot.getPoints() : Map.of();
        DeviceCollectConfig config = DevicePointConfigSupport.normalize(snapshot.getCollectConfig());
        List<DevicePointScanItemVo> items = new ArrayList<>();
        boolean anyValue = false;
        for (DevicePointConfigItem item : config.getPoints()) {
            if (item == null) {
                continue;
            }
            PointAccessType accessType = item.getAccessType() != null
                    ? item.getAccessType()
                    : PointAccessType.READ_ONLY;
            if (!accessType.isCollectable()) {
                continue;
            }
            DevicePointScanItemVo row = new DevicePointScanItemVo();
            row.setCode(item.getCode());
            row.setName(item.getName());
            row.setAddress(item.getActualAddress());
            row.setDataType(item.getDataType());
            if (item.getDataType() != null) {
                row.setDataTypeLabel(item.getDataType().getLabel());
                row.setDataTypeDescription(item.getDataType().getDescription());
            }
            row.setAccessType(item.getAccessType());
            if (item.getAccessType() != null) {
                row.setAccessTypeLabel(item.getAccessType().getLabel());
            }
            row.setUnit(item.getEngUnit());
            DevicePointRealtimeObj pv = DeviceRealtimePointSupport.lookupRealtime(
                    realtimePoints,
                    DevicePointConfigSupport.pointMapKey(item),
                    item.getName());
            if (pv != null) {
                row.setRawValue(pv.getRawValue());
                row.setValue(pv.getValue());
                row.setError(pv.getError());
                if (pv.getRawValue() != null) {
                    anyValue = true;
                }
            } else {
                row.setError(DeviceRealtimePointSupport.UNREAD_ERROR);
            }
            items.add(row);
        }
        vo.setPoints(items);
        vo.setSuccess(anyValue);
        if (items.isEmpty() && vo.getMessage() == null) {
            vo.setMessage("未配置采集点位");
        }
        return vo;
    }

    private DeviceConnectionTestVo doTestConnection(Device device) {
        DeviceProtocol protocol = device.getProtocol();
        if (protocol == null) {
            return DeviceConnectionTestVo.fail("协议不能为空");
        }
        if (!DeviceProtocol.isSupported(protocol)) {
            String label = protocol != null ? protocol.getLabel() : "未知";
            return DeviceConnectionTestVo.fail("暂不支持该协议的连接测试: " + label);
        }
        ProtocolConnect connect = DeviceProtocolConnectFactory.from(device);
        return protocolExecutor.testConnection(connect);
    }

    private void assertCategoryExists(String categoryCode) {
        if (!StringUtils.hasText(categoryCode)) {
            return;
        }
        if (getCategoryByCode(categoryCode.trim()) == null) {
            throw new BusinessException("所属分组不存在: " + categoryCode);
        }
    }

    private DeviceCategory getCategoryByCode(String categoryCode) {
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceCategory::getCode, categoryCode);
        return deviceCategoryService.getOne(wrapper);
    }

    private void fillRealtime(List<DeviceVo> records) {
        for (DeviceVo vo : records) {
            DeviceRealtimeObj realtime = realtimeCacheService.getByDeviceId(vo.getId());
            vo.setRealtime(realtime);
        }
    }

    private Map<String, String> loadCategoryNameMap(List<Device> devices) {
        Set<String> categoryCodes = devices.stream().map(Device::getCategoryCode).filter(StringUtils::hasText).collect(Collectors.toSet());
        if (categoryCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DeviceCategory::getCode, categoryCodes);
        return deviceCategoryService.list(wrapper).stream().collect(Collectors.toMap(DeviceCategory::getCode, DeviceCategory::getName));
    }
}
