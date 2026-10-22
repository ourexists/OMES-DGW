package com.omes.dgw.protocol.modbus;

import java.util.List;

/** 预计算的一块合并读（区、PDU 起止、包含点位）。 */
public record ModbusPollingChunk(String area, int startPdu, int endPdu, List<ModbusPollingSimpleTag> tags) {
}
