package com.omes.dgw.codec.modbus;

/**
 * Modbus 经 PLC4X 下发时的地址与已编码值。
 */
public record ModbusPlcWritePayload(String plcAddress, Object plcValue) {
}
