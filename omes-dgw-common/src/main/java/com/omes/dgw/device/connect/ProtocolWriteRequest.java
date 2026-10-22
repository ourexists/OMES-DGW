package com.omes.dgw.device.connect;

import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;

/**
 * 协议层单次写入参数（控制接口可传入数据类型与 Modbus 字母序字节序）。
 */
public record ProtocolWriteRequest(
        String address,
        Object value,
        PointDataType dataType,
        ModbusByteOrder byteOrder
) {
    public static ProtocolWriteRequest of(String address, Object value) {
        return new ProtocolWriteRequest(address, value, null, null);
    }
}
