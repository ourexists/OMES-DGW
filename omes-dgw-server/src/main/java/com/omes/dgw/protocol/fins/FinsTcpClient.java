package com.omes.dgw.protocol.fins;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Locale;

/**
 * Omron FINS/TCP 客户端：握手、内存区读/写。
 */
public class FinsTcpClient implements Closeable {

    private static final byte[] FINS_HEADER = new byte[] {0x46, 0x49, 0x4E, 0x53};

    private final String host;
    private final int port;
    private final int localNode;
    private final int remoteNode;
    private final int timeoutMs;

    private volatile Socket socket;
    private volatile InputStream input;
    private volatile OutputStream output;
    private int sid;
    /** 握手后用于 FINS 帧 SA1；localNode=0 时由 PLC 分配 */
    private int effectiveLocalNode;
    /** 握手后用于 FINS 帧 DA1；remoteNode=0 时可从 PLC 响应补齐 */
    private int effectiveRemoteNode;
    /** 连接场景：pool / ephemeral / test 等，仅用于日志 */
    private String connectionMode = "client";

    private int maxReadWordsPerChunk = FinsLimits.MAX_WORDS_ABSOLUTE;
    private int maxWriteWordsPerChunk = FinsLimits.DEFAULT_WORDS_PER_WRITE;

    public FinsTcpClient(String host, int port, int localNode, int remoteNode, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.localNode = localNode;
        this.remoteNode = remoteNode;
        this.timeoutMs = timeoutMs;
        this.effectiveLocalNode = localNode;
        this.effectiveRemoteNode = remoteNode;
    }

    public void setConnectionMode(String connectionMode) {
        if (connectionMode != null && !connectionMode.isBlank()) {
            this.connectionMode = connectionMode;
        }
    }

    public synchronized void connect() throws IOException {
        if (isConnected()) {
            return;
        }
        long startNanos = System.nanoTime();
        FinsConnectionLog.tcpConnecting(connectionMode, host, port, localNode, remoteNode, timeoutMs);
        try {
            connectAndHandshake(localNode);
            if (effectiveLocalNode == 0) {
                closeQuietly();
                FinsConnectionLog.handshakeRetry(connectionMode, host, port, 251);
                connectAndHandshake(251);
            }
            if (effectiveLocalNode == 0) {
                throw new IOException(
                        "FINS 握手 SA1=0，PLC 未分配客户端节点；请在 COLLECT_CONFIG 配置 finsLocalNode(1-254)，"
                                + "或关闭其它 FINS 客户端/重启 PLC 以太网模块释放连接表");
            }
            long costMs = (System.nanoTime() - startNanos) / 1_000_000L;
            FinsConnectionLog.handshakeOk(
                    connectionMode, host, port, localNode, remoteNode,
                    effectiveLocalNode, effectiveRemoteNode, costMs);
        } catch (IOException e) {
            FinsConnectionLog.connectFail(connectionMode, host, port, e.getMessage());
            closeQuietly();
            throw e;
        }
    }

    private void connectAndHandshake(int requestLocalNode) throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);
        input = socket.getInputStream();
        output = socket.getOutputStream();
        handshake(requestLocalNode);
    }

    private void closeQuietly() {
        forceClose();
    }

    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * 配置单次 FINS 帧字读/写分片上限（读计划合并块仍会被二次分片到该值）。
     */
    public void setChunkLimits(int maxReadWordsPerChunk, int maxWriteWordsPerChunk) {
        this.maxReadWordsPerChunk = FinsLimits.effectiveMaxWords(
                maxReadWordsPerChunk, FinsLimits.MAX_WORDS_ABSOLUTE);
        this.maxWriteWordsPerChunk = FinsLimits.effectiveMaxWords(
                maxWriteWordsPerChunk, FinsLimits.MAX_WORDS_ABSOLUTE);
    }

    public synchronized int[] readWords(int memoryAreaCode, int wordAddress, int wordCount) throws IOException {
        ensureConnected();
        if (wordCount <= 0) {
            throw new IllegalArgumentException("FINS 字点数须 >= 1");
        }
        if (wordCount <= maxReadWordsPerChunk) {
            return readWordsOnce(memoryAreaCode, wordAddress, wordCount);
        }
        int[] all = new int[wordCount];
        int offset = 0;
        while (offset < wordCount) {
            int chunk = Math.min(maxReadWordsPerChunk, wordCount - offset);
            int[] part = readWordsOnce(memoryAreaCode, wordAddress + offset, chunk);
            System.arraycopy(part, 0, all, offset, chunk);
            offset += chunk;
        }
        return all;
    }

    public synchronized boolean readBit(int memoryAreaCode, int wordAddress, int bitOffset) throws IOException {
        ensureConnected();
        byte[] command = buildReadCommand(memoryAreaCode, wordAddress, bitOffset, 1, true);
        byte[] response = sendCommand(command);
        int endCode = endCode(response);
        if (endCode != 0) {
            throw new IOException("FINS 位读失败 " + formatEndCode(endCode));
        }
        if (response.length < 3) {
            throw new IOException("FINS 位读响应长度异常");
        }
        return (response[2] & 0x01) == 1;
    }

    public synchronized void writeWords(int memoryAreaCode, int wordAddress, int[] words) throws IOException {
        ensureConnected();
        if (words == null || words.length == 0) {
            throw new IllegalArgumentException("FINS 写入字数据不能为空");
        }
        if (words.length <= maxWriteWordsPerChunk) {
            writeWordsOnce(memoryAreaCode, wordAddress, words);
            return;
        }
        int offset = 0;
        while (offset < words.length) {
            int chunk = Math.min(maxWriteWordsPerChunk, words.length - offset);
            int[] part = Arrays.copyOfRange(words, offset, offset + chunk);
            writeWordsOnce(memoryAreaCode, wordAddress + offset, part);
            offset += chunk;
        }
    }

    public synchronized void writeBit(int memoryAreaCode, int wordAddress, int bitOffset, boolean value)
            throws IOException {
        ensureConnected();
        byte[] payload = new byte[9];
        payload[0] = 0x01;
        payload[1] = 0x02;
        payload[2] = (byte) memoryAreaCode;
        writeAddress(payload, 3, wordAddress, bitOffset, true);
        payload[6] = 0x00;
        payload[7] = 0x01;
        payload[8] = (byte) (value ? 0x01 : 0x00);
        byte[] response = sendCommand(buildFinsFrame(payload));
        int endCode = endCode(response);
        if (endCode != 0) {
            throw new IOException("FINS 位写失败 " + formatEndCode(endCode));
        }
    }

    private int[] readWordsOnce(int memoryAreaCode, int wordAddress, int wordCount) throws IOException {
        FinsLimits.clampWords(wordCount, maxReadWordsPerChunk);
        byte[] command = buildReadCommand(memoryAreaCode, wordAddress, 0, wordCount, false);
        byte[] response = sendCommand(command);
        int endCode = endCode(response);
        if (endCode != 0) {
            throw new IOException("FINS 读失败 " + formatEndCode(endCode));
        }
        int dataOffset = 2;
        if (response.length < dataOffset + wordCount * 2) {
            throw new IOException("FINS 读响应长度异常");
        }
        int[] words = new int[wordCount];
        for (int i = 0; i < wordCount; i++) {
            int hi = response[dataOffset + i * 2] & 0xFF;
            int lo = response[dataOffset + i * 2 + 1] & 0xFF;
            words[i] = (hi << 8) | lo;
        }
        return words;
    }

    private void writeWordsOnce(int memoryAreaCode, int wordAddress, int[] words) throws IOException {
        FinsLimits.clampWords(words.length, maxWriteWordsPerChunk);
        byte[] payload = new byte[8 + words.length * 2];
        payload[0] = 0x01;
        payload[1] = 0x02;
        payload[2] = (byte) memoryAreaCode;
        writeAddress(payload, 3, wordAddress, 0, false);
        payload[6] = (byte) ((words.length >> 8) & 0xFF);
        payload[7] = (byte) (words.length & 0xFF);
        for (int i = 0; i < words.length; i++) {
            payload[8 + i * 2] = (byte) ((words[i] >> 8) & 0xFF);
            payload[8 + i * 2 + 1] = (byte) (words[i] & 0xFF);
        }
        byte[] response = sendCommand(buildFinsFrame(payload));
        int endCode = endCode(response);
        if (endCode != 0) {
            throw new IOException("FINS 写失败 " + formatEndCode(endCode));
        }
    }

    @Override
    public synchronized void close() {
        forceClose();
    }

    /**
     * 不依赖 this 监视器关闭底层 socket，可从其它线程打断阻塞读（避免 stop/evict 与轮询死锁）。
     */
    public void forceClose() {
        Socket s = socket;
        socket = null;
        input = null;
        output = null;
        if (s == null) {
            return;
        }
        FinsConnectionLog.close(connectionMode, host, port);
        try {
            if (!s.isClosed()) {
                s.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    private void handshake(int requestLocalNode) throws IOException {
        byte[] request = new byte[20];
        System.arraycopy(FINS_HEADER, 0, request, 0, 4);
        request[4] = 0x00;
        request[5] = 0x00;
        request[6] = 0x00;
        request[7] = 0x0C;
        request[19] = (byte) requestLocalNode;
        writeFully(request);
        byte[] response = readFully(24);
        if (response.length < 24 || response[11] != 0x01) {
            int errorCode = response.length >= 16
                    ? readInt(response, 12)
                    : -1;
            throw new IOException("FINS 握手失败"
                    + (errorCode >= 0 ? " error=0x" + Integer.toHexString(errorCode) : ""));
        }
        int assignedLocalNode = response[19] & 0xFF;
        int reportedRemoteNode = response[23] & 0xFF;
        effectiveLocalNode = requestLocalNode != 0 ? requestLocalNode : assignedLocalNode;
        if (remoteNode != 0) {
            effectiveRemoteNode = remoteNode;
        } else if (reportedRemoteNode != 0) {
            effectiveRemoteNode = reportedRemoteNode;
        }
    }

    public int getEffectiveLocalNode() {
        return effectiveLocalNode;
    }

    public int getEffectiveRemoteNode() {
        return effectiveRemoteNode;
    }

    private byte[] buildReadCommand(int memoryAreaCode, int wordAddress, int bitOffset, int count, boolean bitAccess)
            throws IOException {
        byte[] payload = new byte[8];
        payload[0] = 0x01;
        payload[1] = 0x01;
        payload[2] = (byte) memoryAreaCode;
        writeAddress(payload, 3, wordAddress, bitOffset, bitAccess);
        payload[6] = (byte) ((count >> 8) & 0xFF);
        payload[7] = (byte) (count & 0xFF);
        return buildFinsFrame(payload);
    }

    private byte[] buildFinsFrame(byte[] payload) {
        byte[] frame = new byte[10 + payload.length];
        frame[0] = (byte) 0x80;
        frame[2] = 0x02;
        frame[4] = (byte) effectiveRemoteNode;
        frame[7] = (byte) effectiveLocalNode;
        frame[9] = (byte) (sid++ & 0xFF);
        System.arraycopy(payload, 0, frame, 10, payload.length);
        return frame;
    }

    private byte[] sendCommand(byte[] finsFrame) throws IOException {
        try {
            byte[] tcpFrame = new byte[16 + finsFrame.length];
            System.arraycopy(FINS_HEADER, 0, tcpFrame, 0, 4);
            int length = 8 + finsFrame.length;
            tcpFrame[4] = (byte) ((length >> 24) & 0xFF);
            tcpFrame[5] = (byte) ((length >> 16) & 0xFF);
            tcpFrame[6] = (byte) ((length >> 8) & 0xFF);
            tcpFrame[7] = (byte) (length & 0xFF);
            tcpFrame[11] = 0x02;
            System.arraycopy(finsFrame, 0, tcpFrame, 16, finsFrame.length);
            writeFully(tcpFrame);
            byte[] header = readFully(16);
            int bodyLength = readInt(header, 4) - 8;
            if (bodyLength <= 0) {
                throw new IOException("FINS 响应长度无效");
            }
            byte[] body = readFully(bodyLength);
            if (body.length < 14) {
                throw new IOException("FINS 响应体过短");
            }
            return Arrays.copyOfRange(body, 12, body.length);
        } catch (IOException e) {
            closeQuietly();
            throw e;
        }
    }

    /** FINS 字地址 2 字节为大端（DM100 → 00 64），第 3 字节为位偏移。 */
    private static void writeAddress(byte[] target, int offset, int wordAddress, int bitOffset, boolean bitAccess) {
        target[offset] = (byte) ((wordAddress >> 8) & 0xFF);
        target[offset + 1] = (byte) (wordAddress & 0xFF);
        target[offset + 2] = bitAccess ? (byte) (bitOffset & 0xFF) : 0x00;
    }

    /** sendCommand 已剥离 FINS 头与 MRC/SRC，响应体为 EndCode(2) + Data。 */
    private static int endCode(byte[] response) {
        if (response.length < 2) {
            return -1;
        }
        return ((response[0] & 0xFF) << 8) | (response[1] & 0xFF);
    }

    private static String formatEndCode(int endCode) {
        int mres = (endCode >> 8) & 0xFF;
        int sres = endCode & 0xFF;
        return String.format(
                Locale.ROOT,
                "endCode=0x%04X (MRES=0x%02X SRES=0x%02X)",
                endCode,
                mres,
                sres);
    }

    private void writeFully(byte[] data) throws IOException {
        output.write(data);
        output.flush();
    }

    private byte[] readFully(int length) throws IOException {
        byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            int n = input.read(buffer, read, length - read);
            if (n < 0) {
                throw new IOException("FINS 连接已关闭");
            }
            read += n;
        }
        return buffer;
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24)
                | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8)
                | (data[offset + 3] & 0xFF);
    }
}
