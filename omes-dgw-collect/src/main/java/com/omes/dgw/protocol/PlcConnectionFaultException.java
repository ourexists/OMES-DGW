package com.omes.dgw.protocol;

/**
 * 表示 PLC/Modbus 传输层或设备级异常响应，调用方应踢出 {@link org.apache.plc4x.java.utils.cache.CachedPlcConnectionManager} 缓存并重建连接。
 */
public class PlcConnectionFaultException extends Exception {

    public PlcConnectionFaultException(String message) {
        super(message);
    }

    public PlcConnectionFaultException(String message, Throwable cause) {
        super(message, cause);
    }
}
