package com.omes.dgw.protocol.slmp;

import java.io.IOException;
import java.util.Locale;

/**
 * 对端已按 SLMP/MC 正常应答，但结束代码非 0（软元件/命令级错误，不是 TCP 断开）。
 */
public class SlmpEndCodeException extends IOException {

    private final int endCode;

    public SlmpEndCodeException(int endCode) {
        super(String.format(Locale.ROOT, "SLMP 通信失败 endCode=0x%04X%s", endCode, hint(endCode)));
        this.endCode = endCode;
    }

    public SlmpEndCodeException(int endCode, String extra) {
        super(String.format(Locale.ROOT, "SLMP 通信失败 endCode=0x%04X%s；%s", endCode, hint(endCode), extra));
        this.endCode = endCode;
    }

    public int endCode() {
        return endCode;
    }

    public boolean isCommandOrDataError() {
        return endCode == 0xC058 || endCode == 0xC059 || endCode == 0xC05C;
    }

    private static String hint(int endCode) {
        return switch (endCode) {
            case 0xC050, 0xC051 -> "（PLC 拒绝访问，检查 RUN 中写入/远程口令）";
            case 0xC056 -> "（软元件编号或点数超出 PLC 范围）";
            case 0xC058, 0xC059, 0xC05C -> "（命令/子命令或请求数据与 PLC 帧设置不符）";
            default -> "";
        };
    }
}
