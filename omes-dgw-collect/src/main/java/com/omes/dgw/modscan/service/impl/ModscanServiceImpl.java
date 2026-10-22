package com.omes.dgw.modscan.service.impl;

import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.modscan.dto.*;
import com.omes.dgw.codec.modbus.ModbusRegisterArea;
import com.omes.dgw.modscan.model.ModscanScanResult;
import com.omes.dgw.modscan.service.ModscanService;
import com.omes.dgw.modscan.support.ModscanSessionHandle;
import com.omes.dgw.modscan.support.ModscanSessionManager;
import com.omes.dgw.device.connect.DeviceProtocolConnectFactory;
import com.omes.dgw.device.connect.ProtocolConnect;
import com.omes.dgw.codec.modbus.ModbusTcpConnectionUrl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ModscanServiceImpl implements ModscanService {

    private final DeviceMapper deviceMapper;
    private final ModscanSessionManager sessionManager;

    public ModscanServiceImpl(DeviceMapper deviceMapper, ModscanSessionManager sessionManager) {
        this.deviceMapper = deviceMapper;
        this.sessionManager = sessionManager;
    }

    @Override
    public ModscanSessionVo openSession(ModscanSessionOpenRequest request) {
        Device device = requireModbusTcpDevice(request.getDeviceId());
        ProtocolConnect connect = DeviceProtocolConnectFactory.from(device);
        long startNanos = System.nanoTime();
        ModscanSessionVo vo = new ModscanSessionVo();
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setMaxIdleSeconds(ModscanSessionManager.SESSION_IDLE_TIMEOUT.toSeconds());
        try {
            ModscanSessionHandle handle = sessionManager.open(connect, device.getId());
            vo.setSessionId(handle.sessionId());
            ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.fromDevice(
                    device.getIpAddress(), device.getPort(), device.getConnectTimeoutMs());
            vo.setConnectionTarget(parts.host() + ":" + parts.port());
            vo.setUnitId(parts.unitId());
            vo.setSuccess(true);
            vo.setMessage("连接已建立");
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setMessage(e.getMessage() != null ? e.getMessage() : "连接失败");
        }
        vo.setLatencyMs((System.nanoTime() - startNanos) / 1_000_000L);
        if (!vo.isSuccess()) {
            throw new BusinessException(vo.getMessage());
        }
        return vo;
    }

    @Override
    public ModscanScanVo scan(ModscanScanRequest request) {
        ModscanSessionHandle handle = sessionManager.requireSession(request.getSessionId());
        if (handle == null) {
            throw new BusinessException("扫描会话不存在或已过期，请重新打开连接");
        }
        Device device = deviceMapper.selectById(handle.deviceId());
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        ModbusRegisterArea area = request.getArea();
        if (request.getQuantity() > area.maxScanQuantity()) {
            throw new BusinessException("数量不能超过 " + area.maxScanQuantity());
        }
        ModscanScanResult scan = sessionManager.scan(
                handle, area, request.getStartAddress(), request.getQuantity());
        return toScanVo(device, request, scan);
    }

    @Override
    public void closeSession(ModscanSessionCloseRequest request) {
        if (!sessionManager.close(request.getSessionId())) {
            throw new BusinessException("扫描会话不存在或已关闭");
        }
    }

    @Override
    public List<ModscanRegisterAreaOptionVo> listRegisterAreas() {
        return Arrays.stream(ModbusRegisterArea.values())
                .map(a -> new ModscanRegisterAreaOptionVo(a.getCode(), a.getLabel()))
                .toList();
    }

    private Device requireModbusTcpDevice(String deviceId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if (device.getProtocol() != DeviceProtocol.MODBUS_TCP) {
            throw new BusinessException("仅 Modbus TCP 设备支持 ModScan");
        }
        return device;
    }

    private static ModscanScanVo toScanVo(Device device, ModscanScanRequest request, ModscanScanResult scan) {
        ModscanScanVo vo = new ModscanScanVo();
        vo.setDeviceId(device.getId());
        vo.setDeviceCode(device.getCode());
        vo.setDeviceName(device.getName());
        vo.setArea(request.getArea());
        vo.setAreaLabel(request.getArea().getLabel());
        vo.setStartAddress(request.getStartAddress());
        vo.setQuantity(request.getQuantity());
        vo.setUnitId(scan.getUnitId());
        vo.setConnectionTarget(scan.getConnectionTarget());
        vo.setSuccess(scan.isSuccess());
        vo.setMessage(scan.getMessage());
        vo.setLatencyMs(scan.getLatencyMs());
        vo.setScanTime(LocalDateTime.now());
        List<ModscanScanItemVo> items = new ArrayList<>();
        if (scan.getItems() != null) {
            for (ModscanScanResult.Item row : scan.getItems()) {
                ModscanScanItemVo item = new ModscanScanItemVo();
                item.setOffset(row.getOffset());
                item.setAddress(row.getAddress());
                item.setLegacyAddress(row.getLegacyAddress());
                item.setBinaryValue(row.getBinaryValue());
                items.add(item);
            }
        }
        vo.setItems(items);
        vo.setReturnedCount(items.size());
        return vo;
    }
}
