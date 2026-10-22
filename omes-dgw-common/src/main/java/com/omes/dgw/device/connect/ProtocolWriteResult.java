package com.omes.dgw.device.connect;

import org.springframework.util.StringUtils;

/**
 * 协议层单次写入结果（含校验/设备拒绝等失败原因，供控制指令落库）。
 */
public record ProtocolWriteResult(boolean success, String failureMessage) {

    public static ProtocolWriteResult ok() {
        return new ProtocolWriteResult(true, null);
    }

    public static ProtocolWriteResult fail(String message) {
        String msg = StringUtils.hasText(message) ? message.trim() : "协议写入失败";
        return new ProtocolWriteResult(false, msg);
    }

    public String failureMessageOrDefault(String defaultMessage) {
        return StringUtils.hasText(failureMessage) ? failureMessage : defaultMessage;
    }
}
