package com.omes.dgw.protocol;

import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * 轮询中的传输层/连接层失败（与点位地址、FINS endCode 等配置类错误区分）。
 */
public final class PollTransportFailures {

    private PollTransportFailures() {
    }

    public static boolean isTransportFailure(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        if (isFinsAddressOrProtocolError(reason) || isSlmpAddressOrProtocolError(reason)) {
            return false;
        }
        return reason.contains("Connection reset")
                || reason.contains("Connection refused")
                || reason.contains("Connection timed out")
                || reason.contains("Connect timed out")
                || reason.contains("connect timed out")
                || reason.contains("Broken pipe")
                || reason.contains("No route to host")
                || reason.contains("Network is unreachable")
                || reason.contains("连接已关闭")
                || reason.contains("FINS 握手 SA1=0")
                || reason.contains("FINS 响应长度")
                || reason.contains("FINS 响应体过短")
                || reason.contains("FINS 连接已关闭")
                || reason.contains("SLMP 连接已关闭")
                || reason.contains("SLMP 响应头无效")
                || reason.contains("SLMP 响应长度");
    }

    public static boolean isTransportFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof IllegalArgumentException) {
                return false;
            }
            String msg = current.getMessage();
            if (isFinsAddressOrProtocolError(msg) || isSlmpAddressOrProtocolError(msg)) {
                return false;
            }
            if (isTransportFailure(msg)) {
                return true;
            }
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static boolean hasTransportTagReadError(Map<String, String> tagReadErrors) {
        if (tagReadErrors == null || tagReadErrors.isEmpty()) {
            return false;
        }
        for (String err : tagReadErrors.values()) {
            if (isTransportFailure(err)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFinsAddressOrProtocolError(String msg) {
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("endCode=0x")
                || msg.contains("FINS 读失败")
                || msg.contains("FINS 位读失败")
                || msg.contains("FINS 写失败")
                || msg.contains("FINS 位写失败")
                || msg.contains("不支持的 FINS 地址")
                || msg.contains("FINS 地址不能为空")
                || msg.contains("FINS 单次");
    }

    private static boolean isSlmpAddressOrProtocolError(String msg) {
        if (!StringUtils.hasText(msg)) {
            return false;
        }
        return msg.contains("SLMP 通信失败")
                || msg.contains("SLMP 读响应长度异常")
                || msg.contains("SLMP 位读响应长度异常")
                || msg.contains("SLMP 单次")
                || msg.contains("不支持的 SLMP 地址")
                || msg.contains("SLMP 地址不能为空");
    }
}
