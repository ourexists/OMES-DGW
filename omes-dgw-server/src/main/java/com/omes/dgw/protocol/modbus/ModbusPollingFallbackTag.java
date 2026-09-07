package com.omes.dgw.protocol.modbus;

import com.omes.dgw.device.entity.DevicePoint;

/** Modbus 轮询点读（复杂地址）用的点位。 */
public record ModbusPollingFallbackTag(String tagName, String address, DevicePoint point) {
}
