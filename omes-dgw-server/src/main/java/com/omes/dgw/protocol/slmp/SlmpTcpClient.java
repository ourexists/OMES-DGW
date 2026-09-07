package com.omes.dgw.protocol.slmp;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 三菱 SLMP/MC 协议二进制 TCP 客户端（默认 3E 帧，自动回退 4E；端口默认 5000）。
 */
public class SlmpTcpClient implements Closeable {

    private static final int CMD_BATCH_READ = 0x0401;
    private static final int CMD_BATCH_WRITE = 0x1401;
    private static final int SUB_WORD = 0x0000;
    private static final int SUB_BIT = 0x0001;
    private static final int SUB_WORD_EXT = 0x0002;
    private static final int SUB_BIT_EXT = 0x0003;

    private final String host;
    private final int port;
    private final int networkNo;
    private final int stationNo;
    private final int timeoutMs;
    private final int monitoringTimer;

    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private volatile boolean released;
    private String connectionMode = "client";
    private SlmpFrameFormat frameFormat = SlmpFrameFormat.BINARY_3E;
    private SlmpFrameFormat detectedFrame;
    private boolean extendedDeviceSpec;
    private boolean profileLocked;
    private boolean frameFallbackAttempted;
    private int ioNumber = 0x03FF;
    private Integer stationOverride;
    private int serialNo = 1;

    public SlmpTcpClient(String host, int port, int networkNo, int stationNo, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.networkNo = networkNo;
        this.stationNo = stationNo;
        this.timeoutMs = timeoutMs;
        this.monitoringTimer = monitoringTimerUnits(timeoutMs);
    }

    /**
     * CPU 监视定时器（单位 250ms）须小于 Socket 超时，避免客户端先放弃、PLC 仍在处理。
     */
    static int monitoringTimerUnits(int timeoutMs) {
        int units = Math.max(1, timeoutMs / 250);
        if (units <= 1) {
            return 1;
        }
        return Math.min(0xFFFF, units - 1);
    }

    public void setConnectionMode(String connectionMode) {
        if (connectionMode != null && !connectionMode.isBlank()) {
            this.connectionMode = connectionMode;
        }
    }

    public synchronized void connect() throws IOException {
        if (released) {
            throw new IOException("SLMP 连接已关闭");
        }
        if (isConnected()) {
            return;
        }
        openSocket();
    }

    public synchronized boolean isConnected() {
        return !released && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized int[] readWords(int deviceCode, int deviceNumber, int wordCount) throws IOException {
        ensureConnected();
        if (wordCount <= 0) {
            throw new IllegalArgumentException("SLMP 字点数须 >= 1");
        }
        if (wordCount <= SlmpLimits.MAX_WORDS_PER_REQUEST) {
            return readWordsOnce(deviceCode, deviceNumber, wordCount);
        }
        int[] all = new int[wordCount];
        int offset = 0;
        while (offset < wordCount) {
            int chunk = Math.min(SlmpLimits.MAX_WORDS_PER_REQUEST, wordCount - offset);
            int[] part = readWordsOnce(deviceCode, deviceNumber + offset, chunk);
            System.arraycopy(part, 0, all, offset, chunk);
            offset += chunk;
        }
        return all;
    }

    public synchronized boolean readBit(int deviceCode, int deviceNumber) throws IOException {
        return readBits(deviceCode, deviceNumber, 1)[0];
    }

    public synchronized boolean[] readBits(int deviceCode, int startBitNumber, int bitCount) throws IOException {
        ensureConnected();
        if (bitCount <= 0) {
            throw new IllegalArgumentException("SLMP 位点数须 >= 1");
        }
        if (bitCount <= SlmpLimits.MAX_BITS_PER_REQUEST) {
            return readBitsOnce(deviceCode, startBitNumber, bitCount);
        }
        boolean[] all = new boolean[bitCount];
        int offset = 0;
        while (offset < bitCount) {
            int chunk = Math.min(SlmpLimits.MAX_BITS_PER_REQUEST, bitCount - offset);
            boolean[] part = readBitsOnce(deviceCode, startBitNumber + offset, chunk);
            System.arraycopy(part, 0, all, offset, chunk);
            offset += chunk;
        }
        return all;
    }

    public synchronized void writeWords(int deviceCode, int deviceNumber, int[] words) throws IOException {
        ensureConnected();
        if (words == null || words.length == 0) {
            throw new IllegalArgumentException("SLMP 写入字数据不能为空");
        }
        if (words.length <= SlmpLimits.MAX_WORDS_PER_REQUEST) {
            writeWordsOnce(deviceCode, deviceNumber, words);
            return;
        }
        int offset = 0;
        while (offset < words.length) {
            int chunk = Math.min(SlmpLimits.MAX_WORDS_PER_REQUEST, words.length - offset);
            int[] part = Arrays.copyOfRange(words, offset, offset + chunk);
            writeWordsOnce(deviceCode, deviceNumber + offset, part);
            offset += chunk;
        }
    }

    public synchronized void writeBit(int deviceCode, int deviceNumber, boolean value) throws IOException {
        writeBits(deviceCode, deviceNumber, new boolean[] {value});
    }

    public synchronized void writeBits(int deviceCode, int startBitNumber, boolean[] values) throws IOException {
        ensureConnected();
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("SLMP 写入位数据不能为空");
        }
        if (values.length <= SlmpLimits.MAX_BITS_PER_REQUEST) {
            writeBitsOnce(deviceCode, startBitNumber, values);
            return;
        }
        int offset = 0;
        while (offset < values.length) {
            int chunk = Math.min(SlmpLimits.MAX_BITS_PER_REQUEST, values.length - offset);
            writeBitsOnce(deviceCode, startBitNumber + offset, Arrays.copyOfRange(values, offset, offset + chunk));
            offset += chunk;
        }
    }

    private int[] readWordsOnce(int deviceCode, int deviceNumber, int wordCount) throws IOException {
        SlmpLimits.clampWords(wordCount);
        return withAccessRetry(() -> {
            byte[] response = sendCommand(
                    CMD_BATCH_READ, wordSubcommand(), buildReadDeviceData(deviceCode, deviceNumber, wordCount));
            if (response.length < wordCount * 2) {
                throw new IOException("SLMP 读响应长度异常");
            }
            int[] words = new int[wordCount];
            for (int i = 0; i < wordCount; i++) {
                int lo = response[i * 2] & 0xFF;
                int hi = response[i * 2 + 1] & 0xFF;
                words[i] = lo | (hi << 8);
            }
            return words;
        });
    }

    private boolean[] readBitsOnce(int deviceCode, int startBitNumber, int bitCount) throws IOException {
        SlmpLimits.clampBits(bitCount);
        return withAccessRetry(() -> {
            byte[] response = sendCommand(
                    CMD_BATCH_READ, bitSubcommand(), buildReadDeviceData(deviceCode, startBitNumber, bitCount));
            try {
                return SlmpBitCodec.unpack(response, bitCount);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage());
            }
        });
    }

    private void writeWordsOnce(int deviceCode, int deviceNumber, int[] words) throws IOException {
        SlmpLimits.clampWords(words.length);
        withAccessRetry(() -> {
            byte[] payload = new byte[deviceSpecLength() + words.length * 2];
            writeDeviceSpec(payload, 0, deviceCode, deviceNumber, words.length);
            int dataOff = deviceSpecLength();
            for (int i = 0; i < words.length; i++) {
                payload[dataOff + i * 2] = (byte) (words[i] & 0xFF);
                payload[dataOff + i * 2 + 1] = (byte) ((words[i] >> 8) & 0xFF);
            }
            sendCommand(CMD_BATCH_WRITE, wordSubcommand(), payload);
            return null;
        });
    }

    private void writeBitsOnce(int deviceCode, int startBitNumber, boolean[] values) throws IOException {
        SlmpLimits.clampBits(values.length);
        withAccessRetry(() -> {
            byte[] packed = SlmpBitCodec.pack(values);
            byte[] payload = new byte[deviceSpecLength() + packed.length];
            writeDeviceSpec(payload, 0, deviceCode, startBitNumber, values.length);
            System.arraycopy(packed, 0, payload, deviceSpecLength(), packed.length);
            sendCommand(CMD_BATCH_WRITE, bitSubcommand(), payload);
            return null;
        });
    }

    @Override
    public synchronized void close() {
        forceClose();
    }

    /**
     * 不依赖 this 监视器关闭底层 socket，可从其它线程打断阻塞读。
     * 标记 {@code released} 后禁止 4E 回退再连，避免停采集后留下幽灵 TCP。
     */
    public void forceClose() {
        released = true;
        closeSocket();
    }

    private void closeQuietly() {
        closeSocket();
    }

    private void closeSocket() {
        Socket s = socket;
        if (s != null) {
            SlmpConnectionLog.close(connectionMode, host, port);
            try {
                if (!s.isClosed()) {
                    s.close();
                }
            } catch (IOException ignored) {
            }
        }
        socket = null;
        input = null;
        output = null;
    }

    private void openSocket() throws IOException {
        if (released) {
            throw new IOException("SLMP 连接已关闭");
        }
        long startNanos = System.nanoTime();
        SlmpConnectionLog.tcpConnecting(connectionMode, host, port, networkNo, stationNo, timeoutMs);
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            if (released) {
                closeQuietly();
                throw new IOException("SLMP 连接已关闭");
            }
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            socket.setSoTimeout(timeoutMs);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
            SlmpConnectionLog.connectOk(connectionMode, host, port, costMs);
        } catch (IOException e) {
            SlmpConnectionLog.connectFail(connectionMode, host, port, e.getMessage());
            closeQuietly();
            throw e;
        }
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    private int wordSubcommand() {
        return extendedDeviceSpec ? SUB_WORD_EXT : SUB_WORD;
    }

    private int bitSubcommand() {
        return extendedDeviceSpec ? SUB_BIT_EXT : SUB_BIT;
    }

    private int deviceSpecLength() {
        return extendedDeviceSpec ? 8 : 6;
    }

    private byte[] buildReadDeviceData(int deviceCode, int deviceNumber, int count) {
        byte[] data = new byte[deviceSpecLength()];
        writeDeviceSpec(data, 0, deviceCode, deviceNumber, count);
        return data;
    }

    private void writeDeviceSpec(byte[] target, int offset, int deviceCode, int deviceNumber, int count) {
        target[offset] = (byte) (deviceNumber & 0xFF);
        target[offset + 1] = (byte) ((deviceNumber >> 8) & 0xFF);
        target[offset + 2] = (byte) ((deviceNumber >> 16) & 0xFF);
        if (extendedDeviceSpec) {
            target[offset + 3] = (byte) ((deviceNumber >> 24) & 0xFF);
            target[offset + 4] = (byte) deviceCode;
            target[offset + 5] = 0;
            target[offset + 6] = (byte) (count & 0xFF);
            target[offset + 7] = (byte) ((count >> 8) & 0xFF);
            return;
        }
        target[offset + 3] = (byte) deviceCode;
        target[offset + 4] = (byte) (count & 0xFF);
        target[offset + 5] = (byte) ((count >> 8) & 0xFF);
    }

    private byte[] sendCommand(int command, int subcommand, byte[] requestData) throws IOException {
        try {
            return exchange(command, subcommand, requestData);
        } catch (SlmpEndCodeException e) {
            throw e;
        } catch (IOException e) {
            return fallbackOrRethrow(command, subcommand, requestData, e);
        }
    }

    @FunctionalInterface
    private interface SlmpIo<T> {
        T run() throws IOException;
    }

    private record AccessProfile(boolean extended, int ioNo, int pcNo) {
        String describe() {
            return "spec=" + (extended ? "iQR" : "QnA")
                    + " io=0x" + Integer.toHexString(ioNo).toUpperCase(Locale.ROOT)
                    + " pc=" + pcNo;
        }
    }

    private <T> T withAccessRetry(SlmpIo<T> action) throws IOException {
        if (profileLocked) {
            return action.run();
        }
        SlmpEndCodeException last = null;
        for (AccessProfile profile : buildProfiles()) {
            applyProfile(profile);
            try {
                T result = action.run();
                profileLocked = true;
                SlmpConnectionLog.accessProfile(host, port, profile.describe());
                return result;
            } catch (SlmpEndCodeException e) {
                last = e;
                if (!e.isCommandOrDataError()) {
                    profileLocked = true;
                    throw e;
                }
                SlmpConnectionLog.accessRetry(host, port, profile.describe(), e.getMessage());
            }
        }
        throw new SlmpEndCodeException(
                last != null ? last.endCode() : 0xC05C,
                "已尝试 QnA/iQ-R 软元件描述、I/O 03FF/03E0/0000、站号 "
                        + stationNo + "/255/0，仍返回请求数据错误；请确认 GX Works 中该端口为 Binary 3E/4E，"
                        + "且站号为本站(255)");
    }

    private List<AccessProfile> buildProfiles() {
        LinkedHashSet<Integer> stations = new LinkedHashSet<>();
        stations.add(stationNo);
        stations.add(0xFF);
        stations.add(0x00);
        List<AccessProfile> profiles = new ArrayList<>();
        for (boolean extended : new boolean[] {false, true}) {
            for (int io : new int[] {0x03FF, 0x03E0, 0x0000}) {
                for (int pc : stations) {
                    profiles.add(new AccessProfile(extended, io, pc));
                }
            }
        }
        return profiles;
    }

    private void applyProfile(AccessProfile profile) {
        if (detectedFrame != null) {
            frameFormat = detectedFrame;
        }
        extendedDeviceSpec = profile.extended();
        ioNumber = profile.ioNo();
        stationOverride = profile.pcNo();
    }

    private byte[] fallbackOrRethrow(
            int command, int subcommand, byte[] requestData, IOException error
    ) throws IOException {
        if (shouldTry4E(error) && tryFallbackTo4E()) {
            return exchange(command, subcommand, requestData);
        }
        closeQuietly();
        if (error instanceof SocketTimeoutException timeout) {
            throw new IOException(timeoutHint(timeout), timeout);
        }
        throw error;
    }

    private static boolean shouldTry4E(IOException error) {
        if (error instanceof SocketTimeoutException) {
            return true;
        }
        String msg = error.getMessage();
        if (msg == null) {
            return false;
        }
        if (msg.contains("ASCII") || msg.contains("1E")) {
            return false;
        }
        return msg.contains("响应头无效") || msg.contains("连接已关闭");
    }

    private boolean tryFallbackTo4E() {
        if (released || frameFallbackAttempted || frameFormat == SlmpFrameFormat.BINARY_4E) {
            return false;
        }
        frameFallbackAttempted = true;
        closeQuietly();
        frameFormat = SlmpFrameFormat.BINARY_4E;
        SlmpConnectionLog.frameFallback(host, port, "4E");
        try {
            openSocket();
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private byte[] exchange(int command, int subcommand, byte[] requestData) throws IOException {
        writeFully(buildRequestFrame(command, subcommand, requestData));
        byte[] subheader = readFully(2);
        int b0 = subheader[0] & 0xFF;
        int b1 = subheader[1] & 0xFF;
        int lengthOffset;
        byte[] header;
        if (b0 == 0xD0 && b1 == 0x00) {
            frameFormat = SlmpFrameFormat.BINARY_3E;
            detectedFrame = SlmpFrameFormat.BINARY_3E;
            frameFallbackAttempted = true;
            byte[] rest = readFully(7);
            header = concat(subheader, rest);
            lengthOffset = 7;
        } else if (b0 == 0xD4 && b1 == 0x00) {
            frameFormat = SlmpFrameFormat.BINARY_4E;
            detectedFrame = SlmpFrameFormat.BINARY_4E;
            frameFallbackAttempted = true;
            byte[] rest = readFully(11);
            header = concat(subheader, rest);
            lengthOffset = 11;
        } else {
            throw new IOException(diagnoseHeader(subheader));
        }
        int responseDataLength = (header[lengthOffset] & 0xFF) | ((header[lengthOffset + 1] & 0xFF) << 8);
        if (responseDataLength < 2) {
            throw new IOException("SLMP 响应长度无效");
        }
        byte[] responseData = readFully(responseDataLength);
        int endCode = (responseData[0] & 0xFF) | ((responseData[1] & 0xFF) << 8);
        if (endCode != 0) {
            throw new SlmpEndCodeException(endCode);
        }
        return Arrays.copyOfRange(responseData, 2, responseData.length);
    }

    private byte[] buildRequestFrame(int command, int subcommand, byte[] requestData) {
        int requestDataLength = 2 + 2 + 2 + requestData.length;
        if (frameFormat == SlmpFrameFormat.BINARY_4E) {
            byte[] frame = new byte[13 + requestDataLength];
            frame[0] = 0x54;
            frame[1] = 0x00;
            int serial = serialNo++ & 0xFFFF;
            if (serial == 0) {
                serial = 1;
                serialNo = 2;
            }
            frame[2] = (byte) (serial & 0xFF);
            frame[3] = (byte) ((serial >> 8) & 0xFF);
            frame[4] = 0x00;
            frame[5] = 0x00;
            writeAddressBlock(frame, 6, requestDataLength, command, subcommand, requestData);
            return frame;
        }
        byte[] frame = new byte[9 + requestDataLength];
        frame[0] = 0x50;
        frame[1] = 0x00;
        writeAddressBlock(frame, 2, requestDataLength, command, subcommand, requestData);
        return frame;
    }

    private void writeAddressBlock(
            byte[] frame, int offset, int requestDataLength, int command, int subcommand, byte[] requestData
    ) {
        int pc = stationOverride != null ? stationOverride : stationNo;
        frame[offset] = (byte) networkNo;
        frame[offset + 1] = (byte) pc;
        frame[offset + 2] = (byte) (ioNumber & 0xFF);
        frame[offset + 3] = (byte) ((ioNumber >> 8) & 0xFF);
        frame[offset + 4] = 0x00;
        frame[offset + 5] = (byte) (requestDataLength & 0xFF);
        frame[offset + 6] = (byte) ((requestDataLength >> 8) & 0xFF);
        frame[offset + 7] = (byte) (monitoringTimer & 0xFF);
        frame[offset + 8] = (byte) ((monitoringTimer >> 8) & 0xFF);
        frame[offset + 9] = (byte) (command & 0xFF);
        frame[offset + 10] = (byte) ((command >> 8) & 0xFF);
        frame[offset + 11] = (byte) (subcommand & 0xFF);
        frame[offset + 12] = (byte) ((subcommand >> 8) & 0xFF);
        System.arraycopy(requestData, 0, frame, offset + 13, requestData.length);
    }

    private String timeoutHint(SocketTimeoutException e) {
        return "TCP 已连通，但未收到 SLMP/MC 响应"
                + "；请确认该端口是 SLMP/MC 开放端口（不是 GX Works MELSOFT 编程口），"
                + "通信数据码为 Binary，帧为 3E 或 4E"
                + (e.getMessage() != null ? "：" + e.getMessage() : "");
    }

    private static String diagnoseHeader(byte[] subheader) {
        String hex = toHex(subheader);
        if (subheader.length >= 2) {
            int b0 = subheader[0] & 0xFF;
            int b1 = subheader[1] & 0xFF;
            if (b0 == 0x44 && (b1 == 0x30 || b1 == 0x34)) {
                return "对端使用 ASCII 码（响应头 " + hex + "），请在 GX Works 将通信数据码改为 Binary";
            }
            if (b0 == 0x80 || b0 == 0x81 || b0 == 0x82 || b0 == 0x83) {
                return "对端像 MC 1E 帧（A/FX3 系列，响应头 " + hex + "），当前仅支持 3E/4E 二进制";
            }
        }
        return "SLMP 响应头无效 " + hex
                + "；请确认端口为 SLMP/MC（非 MELSOFT 编程口），帧 3E/4E，数据码 Binary";
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 3);
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%02X", data[i]));
        }
        return sb.toString();
    }

    private void writeFully(byte[] data) throws IOException {
        OutputStream out = output;
        if (out == null || released) {
            throw new IOException("SLMP 连接已关闭");
        }
        out.write(data);
        out.flush();
    }

    private byte[] readFully(int length) throws IOException {
        InputStream in = input;
        if (in == null || released) {
            throw new IOException("SLMP 连接已关闭");
        }
        byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(buffer, read, length - read);
            if (n < 0) {
                throw new IOException("SLMP 连接已关闭");
            }
            read += n;
        }
        return buffer;
    }
}
