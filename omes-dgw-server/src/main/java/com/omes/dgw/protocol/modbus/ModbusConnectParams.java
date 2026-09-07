package com.omes.dgw.protocol.modbus;

/**
 * 设备台账 / 连接参数 JSON 中的 Modbus TCP 连接项（port、unitId、timeout、字节序）。
 */
public record ModbusConnectParams(
        int port,
        int unitId,
        int timeoutMs,
        ModbusByteOrder byteOrder
) {
    public static final int DEFAULT_PORT = 502;
    public static final int DEFAULT_UNIT_ID = 1;
    public static final int DEFAULT_TIMEOUT_MS = 10_000;

    public ModbusConnectParams {
        if (port <= 0) {
            port = DEFAULT_PORT;
        }
        if (unitId < 0) {
            unitId = DEFAULT_UNIT_ID;
        }
        if (timeoutMs <= 0) {
            timeoutMs = DEFAULT_TIMEOUT_MS;
        }
    }
}
