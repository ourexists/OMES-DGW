package com.omes.dgw.device.excel;

import cn.afterturn.easypoi.excel.ExcelExportUtil;
import cn.afterturn.easypoi.excel.ExcelImportUtil;
import cn.afterturn.easypoi.excel.entity.ExportParams;
import cn.afterturn.easypoi.excel.entity.ImportParams;
import cn.afterturn.easypoi.excel.entity.enmus.ExcelType;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.entity.DeviceCategory;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.model.BcdConvertDeserializer;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.codec.modbus.ModbusAddressHelper;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DeviceExcelSupport {

    public static final String SHEET_DEVICES = "设备信息";
    public static final String SHEET_POINTS = "点位";

    private DeviceExcelSupport() {
    }

    public static byte[] exportWorkbook(List<DeviceExcelRow> devices, List<DevicePointExcelRow> points) {
        try (Workbook workbook = buildWorkbook(devices, points);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("导出 Excel 失败: " + e.getMessage());
        }
    }

    public static Workbook buildWorkbook(List<DeviceExcelRow> devices, List<DevicePointExcelRow> points) {
        List<Map<String, Object>> sheets = new ArrayList<>();
        sheets.add(sheetMap(new ExportParams(null, SHEET_DEVICES, ExcelType.XSSF), DeviceExcelRow.class, devices));
        sheets.add(sheetMap(new ExportParams(null, SHEET_POINTS, ExcelType.XSSF), DevicePointExcelRow.class, points));
        return ExcelExportUtil.exportExcel(sheets, ExcelType.XSSF);
    }

    private static Map<String, Object> sheetMap(ExportParams title, Class<?> entity, List<?> data) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("entity", entity);
        map.put("data", data != null ? new ArrayList<>(data) : new ArrayList<>());
        return map;
    }

    public static ParsedWorkbook parseWorkbook(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            SheetLayout layout = detectSheetLayout(bytes);
            List<DeviceExcelRow> devices = importSheet(bytes, layout.deviceIndex(), DeviceExcelRow.class);
            List<DevicePointExcelRow> points = importSheet(bytes, layout.pointIndex(), DevicePointExcelRow.class);
            return new ParsedWorkbook(
                    devices != null ? devices : List.of(),
                    points != null ? points : List.of()
            );
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException e) {
            throw new BusinessException("读取 Excel 失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("解析 Excel 失败: " + e.getMessage());
        }
    }

    private static <T> List<T> importSheet(byte[] bytes, int sheetIndex, Class<T> clazz) throws Exception {
        if (sheetIndex < 0) {
            return List.of();
        }
        ImportParams params = sheetParams(sheetIndex);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            List<T> rows = ExcelImportUtil.importExcel(in, clazz, params);
            return rows != null ? rows : List.of();
        }
    }

    private static SheetLayout detectSheetLayout(byte[] bytes) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            int deviceIndex = indexOfSheet(workbook, SHEET_DEVICES);
            int pointIndex = indexOfSheet(workbook, SHEET_POINTS);
            if (deviceIndex < 0) {
                throw new BusinessException("Excel 须包含「设备信息」工作表");
            }
            if (pointIndex < 0) {
                throw new BusinessException("Excel 须包含「点位」工作表");
            }
            return new SheetLayout(deviceIndex, pointIndex);
        }
    }

    private static int indexOfSheet(Workbook workbook, String name) {
        if (workbook == null || !StringUtils.hasText(name)) {
            return -1;
        }
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            if (sheet != null && name.equals(sheet.getSheetName())) {
                return i;
            }
        }
        return -1;
    }

    private static ImportParams sheetParams(int sheetIndex) {
        ImportParams params = new ImportParams();
        params.setStartSheetIndex(sheetIndex);
        params.setSheetNum(1);
        params.setTitleRows(0);
        params.setHeadRows(1);
        return params;
    }

    public static DeviceExcelRow toDeviceRow(Device device, String categoryName) {
        DeviceExcelRow row = new DeviceExcelRow();
        row.setCode(device.getCode());
        row.setName(device.getName());
        if (device.getProtocol() != null) {
            row.setProtocolLabel(device.getProtocol().getLabel());
        }
        row.setCategoryCode(device.getCategoryCode());
        row.setCategoryName(categoryName);
        row.setIpAddress(device.getIpAddress());
        row.setPort(device.getPort());
        row.setCollectIntervalSec(device.getCollectIntervalSec());
        row.setConnectTimeoutMs(device.getConnectTimeoutMs());
        DeviceCollectConfig config = device.getCollectConfig();
        DeviceProtocol protocol = device.getProtocol();
        if (protocol == DeviceProtocol.FINS_TCP || protocol == DeviceProtocol.SLMP_TCP) {
            row.setReusePollingConnection(
                    config != null && Boolean.TRUE.equals(config.getReusePollingConnection()) ? "是" : "否");
        }
        row.setRemark(device.getRemark());
        return row;
    }

    public static boolean parseOptionalBoolean(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return "true".equalsIgnoreCase(trimmed)
                || "1".equals(trimmed)
                || "是".equals(trimmed)
                || "yes".equalsIgnoreCase(trimmed);
    }

    public static List<DevicePointExcelRow> toPointRows(Device device) {
        DeviceCollectConfig config = device.getCollectConfig();
        if (config == null || config.getPoints() == null || config.getPoints().isEmpty()) {
            return List.of();
        }
        List<DevicePointExcelRow> rows = new ArrayList<>();
        for (DevicePointConfigItem item : config.getPoints()) {
            if (item == null) {
                continue;
            }
            DevicePointExcelRow row = new DevicePointExcelRow();
            row.setDeviceCode(device.getCode());
            row.setCode(item.getCode());
            row.setName(item.getName());
            if (item.getAccessType() != null) {
                row.setAccessTypeLabel(item.getAccessType().getLabel());
            }
            row.setActualAddress(toExcelAddress(device.getProtocol(), item.getActualAddress()));
            row.setSetpointAddress(toExcelAddress(device.getProtocol(), item.getSetpointAddress()));
            if (item.getDataType() != null) {
                row.setDataTypeLabel(item.getDataType().getLabel());
            }
            if (item.getByteOrder() != null) {
                row.setByteOrder(item.getByteOrder().getCode());
            }
            row.setScaleFactor(item.getScaleFactor());
            row.setEngValueMin(item.getEngValueMin());
            row.setEngValueMax(item.getEngValueMax());
            row.setEngUnit(item.getEngUnit());
            row.setDecimalPlaces(item.getDecimalPlaces());
            if (item.getDataType() != null && item.getDataType().isSupportsBcdConvert()) {
                row.setBcdConvert(Boolean.TRUE.equals(item.getBcdConvert()) ? "是" : "否");
            }
            if (item.getDataType() != null && item.getDataType().isSupportsBitField()) {
                row.setBitField(item.getBitField());
            }
            rows.add(row);
        }
        return rows;
    }

    public static Map<String, String> categoryNameByCode(List<DeviceCategory> categories) {
        Map<String, String> map = new LinkedHashMap<>();
        if (categories == null) {
            return map;
        }
        for (DeviceCategory category : categories) {
            if (category != null && StringUtils.hasText(category.getCode())) {
                map.put(category.getCode(), category.getName());
            }
        }
        return map;
    }

    public static void validateDeviceRow(DeviceExcelRow row, int rowIndex) {
        String prefix = deviceRowPrefix(rowIndex);
        requireText(row.getCode(), prefix + "设备编码不能为空");
        requireText(row.getName(), prefix + "设备名称不能为空");
        requireText(row.getProtocolLabel(), prefix + "通信协议不能为空");
        requireText(row.getIpAddress(), prefix + "IP地址不能为空");
        requireNotNull(row.getPort(), prefix + "端口号不能为空");
        requireNotNull(row.getCollectIntervalSec(), prefix + "采集周期不能为空");
        requireNotNull(row.getConnectTimeoutMs(), prefix + "连接超时不能为空");
        DeviceProtocol.fromLabel(row.getProtocolLabel());
    }

    public static DevicePointConfigItem toPointItem(
            DevicePointExcelRow row,
            int rowIndex,
            DeviceProtocol protocol
    ) {
        String prefix = pointRowPrefix(rowIndex);
        requireText(row.getDeviceCode(), prefix + "设备编码不能为空");
        requireText(row.getCode(), prefix + "点位编码不能为空");
        requireText(row.getName(), prefix + "点位名称不能为空");
        requireText(row.getDataTypeLabel(), prefix + "数据类型不能为空");

        PointAccessType accessType = resolveAccessType(row.getAccessTypeLabel(), prefix);
        PointDataType dataType;
        try {
            dataType = PointDataType.fromCode(row.getDataTypeLabel());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(prefix + "数据类型无效: " + row.getDataTypeLabel());
        }

        DevicePointConfigItem item = new DevicePointConfigItem();
        item.setCode(row.getCode().trim());
        item.setName(row.getName().trim());
        item.setAccessType(accessType);
        item.setActualAddress(fromExcelAddress(protocol, row.getActualAddress()));
        item.setSetpointAddress(fromExcelAddress(protocol, row.getSetpointAddress()));
        item.setDataType(dataType);
        if (StringUtils.hasText(row.getByteOrder())) {
            item.setByteOrder(parseByteOrder(row.getByteOrder(), prefix));
        }
        item.setScaleFactor(row.getScaleFactor());
        item.setEngValueMin(row.getEngValueMin());
        item.setEngValueMax(row.getEngValueMax());
        item.setEngUnit(StringUtils.hasText(row.getEngUnit()) ? row.getEngUnit().trim() : null);
        item.setDecimalPlaces(row.getDecimalPlaces());
        item.setBcdConvert(parseBcdConvert(row.getBcdConvert(), prefix));
        item.setBitField(StringUtils.hasText(row.getBitField()) ? row.getBitField().trim() : null);
        DevicePointConfigSupport.preparePointConfig(item, protocol, prefix);
        return item;
    }

    private static PointAccessType resolveAccessType(String label, String prefix) {
        if (!StringUtils.hasText(label)) {
            return PointAccessType.READ_ONLY;
        }
        try {
            PointAccessType accessType = PointAccessType.fromLabel(label);
            if (accessType == null) {
                throw new BusinessException(prefix + "读写类型不能为空（只读 / 读写 / 只写）");
            }
            return accessType;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(prefix + "读写类型无效: " + label.trim() + "，请填写 只读、读写 或 只写");
        }
    }

    public static boolean isBlankRow(DeviceExcelRow row) {
        return row == null || !StringUtils.hasText(row.getCode());
    }

    public static boolean isBlankRow(DevicePointExcelRow row) {
        return row == null || !StringUtils.hasText(row.getDeviceCode());
    }

    private static String toExcelAddress(DeviceProtocol protocol, String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        if (protocol == DeviceProtocol.MODBUS_TCP) {
            return ModbusAddressHelper.toLegacyAddress(address);
        }
        return address;
    }

    private static String fromExcelAddress(DeviceProtocol protocol, String address) {
        if (!StringUtils.hasText(address)) {
            return null;
        }
        String trimmed = address.trim();
        if (protocol == DeviceProtocol.MODBUS_TCP) {
            if (trimmed.contains(":")) {
                return ModbusAddressHelper.toLegacyAddress(
                        ModbusAddressHelper.convertLegacyAddress(trimmed));
            }
            return trimmed;
        }
        return trimmed;
    }

    private static ModbusByteOrder parseByteOrder(String value, String prefix) {
        String trimmed = value.trim();
        try {
            return ModbusByteOrder.fromCode(trimmed);
        } catch (IllegalArgumentException ignored) {
        }
        ModbusByteOrder fromLabel = ModbusByteOrder.fromLabel(trimmed);
        if (fromLabel != null) {
            return fromLabel;
        }
        throw new BusinessException(prefix + "字节序无效: " + trimmed + "，请填写 ABCD、BADC、CDAB 或 DCBA");
    }

    private static Boolean parseBcdConvert(String value, String prefix) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        Boolean parsed = BcdConvertDeserializer.parseText(value);
        if (parsed == null) {
            throw new BusinessException(prefix + "BCD转码无效: " + value.trim() + "，请填写 是 或 否");
        }
        return parsed;
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(message);
        }
    }

    private static void requireNotNull(Object value, String message) {
        if (value == null) {
            throw new BusinessException(message);
        }
    }

    private static String deviceRowPrefix(int rowIndex) {
        return "设备信息第" + rowIndex + "行：";
    }

    private static String pointRowPrefix(int rowIndex) {
        return SHEET_POINTS + "第" + rowIndex + "行：";
    }

    public record ParsedWorkbook(List<DeviceExcelRow> devices, List<DevicePointExcelRow> points) {
    }

    private record SheetLayout(int deviceIndex, int pointIndex) {
    }
}
