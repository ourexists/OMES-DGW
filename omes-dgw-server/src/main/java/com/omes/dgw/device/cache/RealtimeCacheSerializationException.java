package com.omes.dgw.device.cache;

/**
 * 实时快照 JSON 序列化失败；不应计入设备连接熔断。
 */
public class RealtimeCacheSerializationException extends RuntimeException {

    public RealtimeCacheSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
