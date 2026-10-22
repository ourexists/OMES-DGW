package com.omes.dgw.protocol;

import com.omes.dgw.codec.ValueNormalizer;

import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.messages.PlcTagResponse;
import org.apache.plc4x.java.api.messages.PlcWriteRequest;
import org.apache.plc4x.java.api.messages.PlcWriteResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.spi.messages.DefaultPlcWriteRequest;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PLC4X 在 tag 未出现在响应条目 Map 中时，{@link PlcTagResponse#getResponseCode(String)} 可能对 null 调用方法导致 NPE。
 * <p>
 * 写入时还会在 {@code execute()} 内部的 {@code getTagResponseCode}（请求阶段过滤/合并响应）触发同类 NPE，
 * 见 PLC4X 0.13 {@link DefaultPlcWriteRequest#getTagResponseCode(String)}。
 */
public final class PlcTagResponseSupport {

    private PlcTagResponseSupport() {
    }

    /**
     * 构建写入请求后、执行前校验：地址/数值无法被 PLC4X 解析时，避免 execute 阶段 NPE。
     */
    public static Optional<String> validateBuiltWriteRequest(PlcWriteRequest request) {
        if (request == null) {
            return Optional.of("写入请求为空");
        }
        Collection<String> tagNames = request.getTagNames();
        if (tagNames == null || tagNames.isEmpty()) {
            return Optional.of("写入未包含任何标签");
        }
        for (String tag : tagNames) {
            PlcResponseCode code = safeWriteRequestTagCode(request, tag);
            if (code == null) {
                return Optional.of("写入标签未就绪，请检查 Modbus 地址与数据类型是否匹配");
            }
            if (code != PlcResponseCode.OK) {
                return Optional.of("写入地址或数值无效: " + code.name());
            }
        }
        return Optional.empty();
    }

    /**
     * 安全读取写入请求上某 tag 的预检码，不调用 {@link PlcWriteRequest#getTagResponseCode(String)}。
     */
    public static PlcResponseCode safeWriteRequestTagCode(PlcWriteRequest request, String tagName) {
        if (request == null || !StringUtils.hasText(tagName)) {
            return null;
        }
        if (request instanceof DefaultPlcWriteRequest wr) {
            try {
                var item = wr.getTagValueItem(tagName);
                return item != null ? item.getResponseCode() : null;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        try {
            return request.getTagResponseCode(tagName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 执行已构建的写入请求；将 PLC4X 0.13 的 NPE 转为可读失败信息。
     */
    public static PlcWriteResponse awaitWriteResponse(PlcWriteRequest request, long timeoutMs) throws Exception {
        Optional<String> validation = validateBuiltWriteRequest(request);
        if (validation.isPresent()) {
            throw new IllegalArgumentException(validation.get());
        }
        try {
            return request.execute().get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new IllegalArgumentException("写入超时");
        } catch (ExecutionException e) {
            throw toWriteException(e.getCause());
        } catch (RuntimeException e) {
            throw toWriteException(e);
        }
    }

    public static String toWriteFailureMessage(Throwable error) {
        if (error == null) {
            return "PLC 写入失败";
        }
        if (error instanceof IllegalArgumentException iae) {
            String msg = iae.getMessage();
            if (StringUtils.hasText(msg)) {
                return msg;
            }
        }
        if (error instanceof NullPointerException) {
            return "写入地址或数值格式无效，请检查 Modbus 地址与数据类型是否匹配";
        }
        String msg = error.getMessage();
        if (StringUtils.hasText(msg) && msg.contains("PlcTagValueItem.getResponseCode")) {
            return "写入地址或数值格式无效，请检查 Modbus 地址与数据类型是否匹配";
        }
        return StringUtils.hasText(msg) ? msg : error.getClass().getSimpleName();
    }

    private static Exception toWriteException(Throwable error) {
        if (error instanceof Exception ex) {
            String msg = toWriteFailureMessage(error);
            if (ex instanceof IllegalArgumentException) {
                return new IllegalArgumentException(msg, ex);
            }
            return new Exception(msg, ex);
        }
        return new Exception(toWriteFailureMessage(error), error);
    }

    /**
     * Modbus 设备异常码/内部错误等，继续复用缓存连接可能导致解码异常或连接僵死（PLC4X 0.13 已知问题）。
     */
    public static boolean isConnectionInvalidatingResponseCode(PlcResponseCode code) {
        if (code == null) {
            return false;
        }
        return code == PlcResponseCode.REMOTE_ERROR
                || code == PlcResponseCode.INTERNAL_ERROR
                || code == PlcResponseCode.REMOTE_BUSY;
    }

    public static PlcResponseCode safeResponseCode(PlcTagResponse response, String tagName) {
        if (response == null || !StringUtils.hasText(tagName)) {
            return null;
        }
        try {
            Collection<String> tagNames = response.getTagNames();
            if (tagNames == null || !tagNames.contains(tagName)) {
                return null;
            }
            return response.getResponseCode(tagName);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 批读全部 tag 均为设备/协议级错误（非单纯坏地址）时，建议踢出缓存连接。
     */
    public static boolean batchReadIndicatesConnectionFault(PlcReadResponse response, List<String> tagNames) {
        if (response == null || tagNames == null || tagNames.isEmpty()) {
            return false;
        }
        boolean anyOk = false;
        boolean anyInvalidating = false;
        boolean anyAddressError = false;
        for (String tag : tagNames) {
            PlcResponseCode code = safeResponseCode(response, tag);
            if (code == null) {
                continue;
            }
            if (code == PlcResponseCode.OK) {
                anyOk = true;
            } else if (isConnectionInvalidatingResponseCode(code)) {
                anyInvalidating = true;
            } else if (code == PlcResponseCode.INVALID_ADDRESS
                    || code == PlcResponseCode.NOT_FOUND
                    || code == PlcResponseCode.INVALID_DATATYPE
                    || code == PlcResponseCode.INVALID_DATA) {
                anyAddressError = true;
            }
        }
        return anyInvalidating && !anyOk && !anyAddressError;
    }

    public static boolean isTransportOrConnectionFailure(Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof PlcConnectionFaultException
                || error instanceof PlcConnectionException
                || error instanceof TimeoutException
                || error instanceof java.io.IOException) {
            return true;
        }
        String msg = error.getMessage();
        if (StringUtils.hasText(msg)) {
            String lower = msg.toLowerCase();
            if (lower.contains("not connected")
                    || lower.contains("connection")
                    || lower.contains("timeout")
                    || lower.contains("timed out")
                    || lower.contains("channel")
                    || lower.contains("netty")) {
                return true;
            }
        }
        Throwable cause = error.getCause();
        return cause != null && cause != error && isTransportOrConnectionFailure(cause);
    }

    public static boolean isTagOk(PlcTagResponse response, String tagName) {
        if (response == null || !StringUtils.hasText(tagName)) {
            return false;
        }
        try {
            Collection<String> tagNames = response.getTagNames();
            if (tagNames == null || !tagNames.contains(tagName)) {
                return false;
            }
            return PlcResponseCode.OK.equals(response.getResponseCode(tagName));
        } catch (RuntimeException ignored) {
            // PLC4X 0.13：tag 在 getTagNames 中但内部 Map 值为 null 时会 NPE
            return false;
        }
    }

    public static Object getObject(PlcReadResponse response, String tagName) {
        if (response == null || !StringUtils.hasText(tagName)) {
            return null;
        }
        if (!isTagOk(response, tagName)) {
            return null;
        }
        try {
            return ValueNormalizer.toJava(response.getObject(tagName));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 优先直接取对象，规避 PLC4X 0.13 在 {@link #isTagOk} / {@code getResponseCode} 上的 NPE。
     */
    public static Object getObjectLenient(PlcReadResponse response, String tagName) {
        if (response == null || !StringUtils.hasText(tagName)) {
            return null;
        }
        try {
            Object value = ValueNormalizer.toJava(response.getObject(tagName));
            if (value != null) {
                return value;
            }
        } catch (RuntimeException ignored) {
        }
        return getObject(response, tagName);
    }
}
