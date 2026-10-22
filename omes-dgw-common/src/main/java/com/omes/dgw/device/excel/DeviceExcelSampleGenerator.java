package com.omes.dgw.device.excel;

import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 本地生成 Excel 示例：{@code mvn -q compile exec:java -Dexec.mainClass=com.omes.dgw.device.excel.DeviceExcelSampleGenerator}
 */
public final class DeviceExcelSampleGenerator {

    private DeviceExcelSampleGenerator() {
    }

    public static void main(String[] args) throws Exception {
        DeviceExcelRow device = new DeviceExcelRow();
        device.setCode("DEMO_MODBUS_01");
        device.setName("示例Modbus设备");
        device.setProtocolLabel(DeviceProtocol.MODBUS_TCP.getLabel());
        device.setCategoryName("可选，填写系统中已有分组编号");
        device.setIpAddress("192.168.1.100");
        device.setPort(502);
        device.setCollectIntervalSec(5);
        device.setConnectTimeoutMs(3000);
        device.setReusePollingConnection("否");
        device.setRemark("导入前请将分组编号改为系统中已存在的值；只读填实际值点位，只写填设定值点位，读写两者都填");

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

        byte[] bytes = DeviceExcelSupport.exportWorkbook(
                List.of(device),
                List.of(temperature, hexCode, statusA, progStep, progSeg, status, setpoint, enable));
        Path out = Path.of("src/main/resources/excel/device-import-example.xlsx");
        Files.createDirectories(out.getParent());
        Files.write(out, bytes);
        System.out.println("Written: " + out.toAbsolutePath());
    }
}
