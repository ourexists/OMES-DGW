package com.omes.dgw.protocol.fins;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import com.omes.dgw.protocol.modbus.ModbusNumericCodec;

/**
 * 将 FINS 字读结果按点位数据类型解码（复用 Modbus 多字组字逻辑）。
 */
public final class FinsNumericCodec {

    private FinsNumericCodec() {
    }

    public static Object decodeWords(int[] words, DevicePoint point, ModbusByteOrder deviceByteOrder) {
        if (words == null || words.length == 0 || point == null || point.getDataType() == null) {
            return null;
        }
        PointDataType dataType = point.getDataType();
        if (dataType.isBoolType()) {
            Boolean decoded = ModbusNumericCodec.decodeBool(words[0] & 0x01, dataType);
            return decoded != null ? decoded : ((words[0] & 0x01) == 1);
        }
        ModbusByteOrder order = ModbusNumericCodec.resolve(point.getByteOrder(), deviceByteOrder);
        return ModbusNumericCodec.decodeFromBlock(
                words,
                0,
                point.getDataType(),
                order,
                point.getStringLength());
    }

    public static int[] encodeWriteWords(Object value, DevicePoint point, ModbusByteOrder deviceByteOrder) {
        if (point == null) {
            throw new IllegalArgumentException("写入值与数据类型不匹配");
        }
        return encodeWriteWords(
                value,
                point.getDataType(),
                point.getByteOrder(),
                point.getStringLength(),
                deviceByteOrder);
    }

    public static int[] encodeWriteWords(
            Object value,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength,
            ModbusByteOrder deviceByteOrder
    ) {
        if (dataType == null || value == null) {
            throw new IllegalArgumentException("写入值与数据类型不匹配");
        }
        ModbusByteOrder order = ModbusNumericCodec.resolve(byteOrder, deviceByteOrder);
        int[] words = ModbusNumericCodec.encodeToRegisters(value, dataType, order, stringLength);
        if (words == null || words.length == 0) {
            throw new IllegalArgumentException("写入值与数据类型不匹配");
        }
        return words;
    }

    public static boolean encodeWriteBit(Object value, DevicePoint point) {
        if (point == null) {
            throw new IllegalArgumentException("写入值与布尔类型不匹配");
        }
        return encodeWriteBit(value, point.getDataType());
    }

    public static boolean encodeWriteBit(Object value, PointDataType dataType) {
        Boolean wire = ModbusNumericCodec.encodeBoolWire(value, dataType != null ? dataType : PointDataType.BOOL);
        if (wire == null) {
            throw new IllegalArgumentException("写入值与布尔类型不匹配");
        }
        return wire;
    }
}
