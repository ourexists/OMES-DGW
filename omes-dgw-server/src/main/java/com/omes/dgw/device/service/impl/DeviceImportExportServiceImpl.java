package com.omes.dgw.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.dto.DeviceImportResultVo;
import com.omes.dgw.device.dto.DeviceQuery;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.entity.DeviceCategory;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import com.omes.dgw.device.excel.DeviceExcelRow;
import com.omes.dgw.device.excel.DeviceExcelSupport;
import com.omes.dgw.device.excel.DevicePointExcelRow;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.service.DeviceCategoryService;
import com.omes.dgw.device.service.DeviceImportExportService;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DeviceImportExportServiceImpl implements DeviceImportExportService {

    private final DeviceMapper deviceMapper;
    private final DeviceCategoryService deviceCategoryService;

    public DeviceImportExportServiceImpl(
            DeviceMapper deviceMapper,
            DeviceCategoryService deviceCategoryService
    ) {
        this.deviceMapper = deviceMapper;
        this.deviceCategoryService = deviceCategoryService;
    }

    @Override
    public byte[] exportExcel(DeviceQuery query) {
        List<Device> devices = listDevices(query);
        Map<String, String> categoryNames = loadCategoryNameMap(devices);
        List<DeviceExcelRow> deviceRows = new ArrayList<>();
        List<DevicePointExcelRow> pointRows = new ArrayList<>();
        for (Device device : devices) {
            String categoryName = StringUtils.hasText(device.getCategoryCode())
                    ? categoryNames.get(device.getCategoryCode())
                    : null;
            deviceRows.add(DeviceExcelSupport.toDeviceRow(device, categoryName));
            pointRows.addAll(DeviceExcelSupport.toPointRows(device));
        }
        return DeviceExcelSupport.exportWorkbook(deviceRows, pointRows);
    }

    @Override
    public byte[] downloadTemplate() {
        return DeviceExcelSupport.exportWorkbook(sampleDevices(), samplePoints());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceImportResultVo importExcel(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.endsWith(".xlsx") && !filename.endsWith(".xls")) {
            throw new BusinessException("仅支持 .xlsx 或 .xls 文件");
        }

        DeviceExcelSupport.ParsedWorkbook parsed;
        try {
            parsed = DeviceExcelSupport.parseWorkbook(file.getInputStream());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("读取 Excel 失败: " + ex.getMessage());
        }

        List<DeviceExcelRow> deviceRows = parsed.devices().stream()
                .filter(row -> !DeviceExcelSupport.isBlankRow(row))
                .toList();
        if (deviceRows.isEmpty()) {
            throw new BusinessException("设备信息表为空，请至少填写一行设备");
        }

        Map<String, List<DevicePointConfigItem>> pointsByDeviceCode = parsePoints(deviceRows, parsed.points());
        Set<String> codesInFile = new HashSet<>();
        int created = 0;
        int updated = 0;
        int pointCount = 0;

        int deviceRowIndex = 2;
        for (DeviceExcelRow row : deviceRows) {
            DeviceExcelSupport.validateDeviceRow(row, deviceRowIndex);
            String code = row.getCode().trim();
            if (!codesInFile.add(code)) {
                throw new BusinessException("设备信息表存在重复设备编码: " + code);
            }
            DeviceProtocol protocol = DeviceProtocol.fromLabel(row.getProtocolLabel());
            String categoryCode = StringUtils.hasText(row.getCategoryCode()) ? row.getCategoryCode().trim() : null;
            if (categoryCode != null) {
                assertCategoryExists(categoryCode);
            }

            Device existing = findByCode(code);
            boolean isCreate = existing == null;
            Device entity = isCreate ? new Device() : existing;
            entity.setCode(code);
            entity.setName(row.getName().trim());
            entity.setProtocol(protocol);
            entity.setCategoryCode(categoryCode);
            entity.setIpAddress(row.getIpAddress().trim());
            entity.setPort(row.getPort());
            entity.setCollectIntervalSec(row.getCollectIntervalSec());
            entity.setConnectTimeoutMs(row.getConnectTimeoutMs());
            if (isCreate) {
                entity.setEnabled(false);
            }
            entity.setRemark(StringUtils.hasText(row.getRemark()) ? row.getRemark().trim() : null);

            List<DevicePointConfigItem> points = pointsByDeviceCode.getOrDefault(code, List.of());
            DeviceCollectConfig config = isCreate || existing.getCollectConfig() == null
                    ? new DeviceCollectConfig()
                    : existing.getCollectConfig();
            config.setReusePollingConnection(DeviceExcelSupport.parseOptionalBoolean(row.getReusePollingConnection()));
            config.setPoints(points);
            entity.setCollectConfig(DevicePointConfigSupport.normalize(config, protocol));
            pointCount += points.size();

            if (isCreate) {
                deviceMapper.insert(entity);
                created++;
            } else {
                deviceMapper.updateById(entity);
                updated++;
            }
            deviceRowIndex++;
        }

        validateOrphanPoints(pointsByDeviceCode, codesInFile);

        DeviceImportResultVo result = new DeviceImportResultVo();
        result.setCreatedDevices(created);
        result.setUpdatedDevices(updated);
        result.setImportedPoints(pointCount);
        result.setMessage(String.format(
                "导入完成：新增设备 %d 台，更新设备 %d 台，点位 %d 条",
                created, updated, pointCount));
        return result;
    }

    private Map<String, List<DevicePointConfigItem>> parsePoints(
            List<DeviceExcelRow> deviceRows,
            List<DevicePointExcelRow> pointRows
    ) {
        Map<String, List<DevicePointConfigItem>> grouped = new LinkedHashMap<>();
        Map<String, DeviceProtocol> protocolByCode = new HashMap<>();
        if (pointRows == null) {
            return grouped;
        }
        int rowIndex = 2;
        for (DevicePointExcelRow row : pointRows) {
            if (DeviceExcelSupport.isBlankRow(row)) {
                continue;
            }
            String deviceCode = row.getDeviceCode().trim();
            DeviceProtocol protocol = protocolByCode.computeIfAbsent(
                    deviceCode,
                    code -> resolveProtocolForImport(code, deviceRows)
            );
            DevicePointConfigItem item = DeviceExcelSupport.toPointItem(row, rowIndex, protocol);
            grouped.computeIfAbsent(deviceCode, k -> new ArrayList<>()).add(item);
            rowIndex++;
        }
        for (Map.Entry<String, List<DevicePointConfigItem>> entry : grouped.entrySet()) {
            Set<String> pointCodes = new HashSet<>();
            for (DevicePointConfigItem item : entry.getValue()) {
                if (!pointCodes.add(item.getCode())) {
                    throw new BusinessException("设备「" + entry.getKey() + "」存在重复点位编码: " + item.getCode());
                }
            }
        }
        return grouped;
    }

    private DeviceProtocol resolveProtocolForImport(String deviceCode, List<DeviceExcelRow> deviceRows) {
        for (DeviceExcelRow row : deviceRows) {
            if (row != null && StringUtils.hasText(row.getCode()) && deviceCode.equals(row.getCode().trim())) {
                return DeviceProtocol.fromLabel(row.getProtocolLabel());
            }
        }
        Device device = findByCode(deviceCode);
        if (device != null && device.getProtocol() != null) {
            return device.getProtocol();
        }
        throw new BusinessException("无法确定设备「" + deviceCode + "」的通信协议，请在设备信息表中补充该设备");
    }

    private void validateOrphanPoints(Map<String, List<DevicePointConfigItem>> pointsByDeviceCode, Set<String> codesInFile) {
        for (String deviceCode : pointsByDeviceCode.keySet()) {
            if (!codesInFile.contains(deviceCode) && findByCode(deviceCode) == null) {
                throw new BusinessException("点位表中存在未在设备信息表定义的设备编码: " + deviceCode);
            }
        }
    }

    private List<Device> listDevices(DeviceQuery query) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            wrapper.like(StringUtils.hasText(query.getName()), Device::getName, query.getName())
                    .like(StringUtils.hasText(query.getCode()), Device::getCode, query.getCode())
                    .eq(query.getProtocol() != null, Device::getProtocol, query.getProtocol())
                    .eq(StringUtils.hasText(query.getCategoryCode()), Device::getCategoryCode, query.getCategoryCode())
                    .eq(query.getEnabled() != null, Device::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(Device::getCreateTime);
        return deviceMapper.selectList(wrapper);
    }

    private Device findByCode(String code) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getCode, code);
        return deviceMapper.selectOne(wrapper);
    }

    private void assertCategoryExists(String categoryCode) {
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DeviceCategory::getCode, categoryCode);
        if (deviceCategoryService.getOne(wrapper) == null) {
            throw new BusinessException("所属分组不存在: " + categoryCode);
        }
    }

    private Map<String, String> loadCategoryNameMap(List<Device> devices) {
        Set<String> categoryCodes = devices.stream()
                .map(Device::getCategoryCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (categoryCodes.isEmpty()) {
            return new HashMap<>();
        }
        LambdaQueryWrapper<DeviceCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DeviceCategory::getCode, categoryCodes);
        return DeviceExcelSupport.categoryNameByCode(deviceCategoryService.list(wrapper));
    }

    private static List<DeviceExcelRow> sampleDevices() {
        DeviceExcelRow row = new DeviceExcelRow();
        row.setCode("DEMO_MODBUS_01");
        row.setName("示例Modbus设备");
        row.setProtocolLabel(DeviceProtocol.MODBUS_TCP.getLabel());
        row.setCategoryName("可选，填写系统中已有分组编号");
        row.setIpAddress("192.168.1.100");
        row.setPort(502);
        row.setCollectIntervalSec(5);
        row.setConnectTimeoutMs(3000);
        row.setReusePollingConnection("否");
        row.setRemark("导入前请将分组编号改为系统中已存在的值；只读填实际值点位，只写填设定值点位，读写两者都填");
        return List.of(row);
    }

    private static List<DevicePointExcelRow> samplePoints() {
        DevicePointExcelRow temperature = new DevicePointExcelRow();
        temperature.setDeviceCode("DEMO_MODBUS_01");
        temperature.setCode("TEMP");
        temperature.setName("温度");
        temperature.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        temperature.setActualAddress("40001");
        temperature.setDataTypeLabel(PointDataType.REAL.getLabel());
        temperature.setByteOrder(ModbusByteOrder.CDAB.getCode());
        temperature.setScaleFactor(new BigDecimal("0.1"));
        temperature.setEngValueMin(new BigDecimal("0"));
        temperature.setEngValueMax(new BigDecimal("100"));
        temperature.setEngUnit("℃");
        temperature.setDecimalPlaces(1);

        DevicePointExcelRow hexCode = new DevicePointExcelRow();
        hexCode.setDeviceCode("DEMO_MODBUS_01");
        hexCode.setCode("STATUS_WORD");
        hexCode.setName("状态字");
        hexCode.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        hexCode.setActualAddress("40002");
        hexCode.setDataTypeLabel(PointDataType.UINT.getLabel());
        hexCode.setScaleFactor(BigDecimal.ONE);
        hexCode.setEngValueMin(BigDecimal.ZERO);
        hexCode.setEngValueMax(new BigDecimal("65535"));
        hexCode.setDecimalPlaces(0);
        hexCode.setBcdConvert("是");

        DevicePointExcelRow statusA = new DevicePointExcelRow();
        statusA.setDeviceCode("DEMO_MODBUS_01");
        statusA.setCode("STATUS_A");
        statusA.setName("运行状态(A位)");
        statusA.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        statusA.setActualAddress("40002");
        statusA.setDataTypeLabel(PointDataType.UINT.getLabel());
        statusA.setScaleFactor(BigDecimal.ONE);
        statusA.setEngValueMin(BigDecimal.ZERO);
        statusA.setEngValueMax(new BigDecimal("15"));
        statusA.setDecimalPlaces(0);
        statusA.setBcdConvert("否");
        statusA.setBitField("A");

        DevicePointExcelRow progStep = new DevicePointExcelRow();
        progStep.setDeviceCode("DEMO_MODBUS_01");
        progStep.setCode("PROG_STEP");
        progStep.setName("当前程序步(BA)");
        progStep.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        progStep.setActualAddress("40008");
        progStep.setDataTypeLabel(PointDataType.UINT.getLabel());
        progStep.setScaleFactor(BigDecimal.ONE);
        progStep.setEngValueMin(BigDecimal.ONE);
        progStep.setEngValueMax(new BigDecimal("30"));
        progStep.setDecimalPlaces(0);
        progStep.setBcdConvert("是");
        progStep.setBitField("BA");

        DevicePointExcelRow progSeg = new DevicePointExcelRow();
        progSeg.setDeviceCode("DEMO_MODBUS_01");
        progSeg.setCode("PROG_SEG");
        progSeg.setName("当前段号(DC)");
        progSeg.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        progSeg.setActualAddress("40008");
        progSeg.setDataTypeLabel(PointDataType.UINT.getLabel());
        progSeg.setScaleFactor(BigDecimal.ONE);
        progSeg.setEngValueMin(BigDecimal.ZERO);
        progSeg.setEngValueMax(new BigDecimal("99"));
        progSeg.setDecimalPlaces(0);
        progSeg.setBcdConvert("是");
        progSeg.setBitField("DC");

        DevicePointExcelRow status = new DevicePointExcelRow();
        status.setDeviceCode("DEMO_MODBUS_01");
        status.setCode("RUN");
        status.setName("运行状态");
        status.setAccessTypeLabel(PointAccessType.READ_ONLY.getLabel());
        status.setActualAddress("10001");
        status.setDataTypeLabel(PointDataType.BOOL.getLabel());
        status.setScaleFactor(BigDecimal.ONE);
        status.setDecimalPlaces(0);

        DevicePointExcelRow setpoint = new DevicePointExcelRow();
        setpoint.setDeviceCode("DEMO_MODBUS_01");
        setpoint.setCode("SET_TEMP");
        setpoint.setName("温度设定");
        setpoint.setAccessTypeLabel(PointAccessType.WRITE_ONLY.getLabel());
        setpoint.setSetpointAddress("40010");
        setpoint.setDataTypeLabel(PointDataType.REAL.getLabel());
        setpoint.setByteOrder(ModbusByteOrder.CDAB.getCode());
        setpoint.setScaleFactor(new BigDecimal("0.1"));
        setpoint.setEngValueMin(new BigDecimal("0"));
        setpoint.setEngValueMax(new BigDecimal("100"));
        setpoint.setEngUnit("℃");
        setpoint.setDecimalPlaces(1);

        DevicePointExcelRow enable = new DevicePointExcelRow();
        enable.setDeviceCode("DEMO_MODBUS_01");
        enable.setCode("ENABLE");
        enable.setName("使能开关");
        enable.setAccessTypeLabel(PointAccessType.READ_WRITE.getLabel());
        enable.setActualAddress("00001");
        enable.setSetpointAddress("00001");
        enable.setDataTypeLabel(PointDataType.BOOL.getLabel());
        enable.setScaleFactor(BigDecimal.ONE);
        enable.setDecimalPlaces(0);

        return List.of(temperature, hexCode, statusA, progStep, progSeg, status, setpoint, enable);
    }
}
