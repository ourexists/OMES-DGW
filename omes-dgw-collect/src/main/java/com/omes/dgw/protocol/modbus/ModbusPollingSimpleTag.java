package com.omes.dgw.protocol.modbus;

import com.omes.dgw.device.entity.DevicePoint;

/** Modbus 轮询块读用的简单 PLC4X 点位。 */
public record ModbusPollingSimpleTag(String tagName, String area, int pduOffset, DevicePoint point) {
}
