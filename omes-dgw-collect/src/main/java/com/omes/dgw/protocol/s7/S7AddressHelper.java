package com.omes.dgw.protocol.s7;

import com.github.s7connector.api.DaveArea;
import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与 omes S7 轮询相同的地址写法。V 区按 S7-200 Smart 映射到 DB1。
 */
public final class S7AddressHelper {

    /** 单次读取最大字节数，需小于 S7-200 Smart 的 PDU 负载（约 222）。 */
    public static final int MAX_READ_BYTES = 200;

    private static final Pattern V_BWD = Pattern.compile("V([BWD])(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_BIT = Pattern.compile("V(\\d+)\\.(\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IQM_BWD = Pattern.compile("([IQM])([BWD])(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern IQM_BIT = Pattern.compile("([IQM])(\\d+)\\.(\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DB_BWD = Pattern.compile("DB(\\d+)\\.DB([BWD])(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DB_BIT = Pattern.compile("DB(\\d+)\\.DBX(\\d+)\\.(\\d)", Pattern.CASE_INSENSITIVE);

    private S7AddressHelper() {
    }

    public record S7Address(DaveArea area, int areaNumber, int offset, int bitNumber, char suffix) {

        public boolean bit() {
            return bitNumber >= 0;
        }

        public int suffixBytes() {
            if (bit()) {
                return 1;
            }
            return switch (suffix) {
                case 'B' -> 1;
                case 'W' -> 2;
                case 'D' -> 4;
                default -> throw new IllegalArgumentException("Unknown type: " + suffix);
            };
        }

        public String groupKey() {
            return area.name() + ":" + areaNumber;
        }
    }

    public record S7ReadSpan(String tag, S7Address address, int byteCount) {
    }

    public static S7Address parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("S7 地址不能为空");
        }
        String addr = raw.trim().toUpperCase();
        Matcher matcher = V_BWD.matcher(addr);
        if (matcher.matches()) {
            char type = matcher.group(1).charAt(0);
            return new S7Address(DaveArea.DB, 1, Integer.parseInt(matcher.group(2)), -1, type);
        }
        matcher = V_BIT.matcher(addr);
        if (matcher.matches()) {
            return bitAddress(DaveArea.DB, 1, matcher.group(1), matcher.group(2));
        }
        matcher = IQM_BWD.matcher(addr);
        if (matcher.matches()) {
            char type = matcher.group(2).charAt(0);
            return new S7Address(areaOf(matcher.group(1).charAt(0)), 0, Integer.parseInt(matcher.group(3)), -1, type);
        }
        matcher = IQM_BIT.matcher(addr);
        if (matcher.matches()) {
            return bitAddress(areaOf(matcher.group(1).charAt(0)), 0, matcher.group(2), matcher.group(3));
        }
        matcher = DB_BIT.matcher(addr);
        if (matcher.matches()) {
            return bitAddress(DaveArea.DB, Integer.parseInt(matcher.group(1)), matcher.group(2), matcher.group(3));
        }
        matcher = DB_BWD.matcher(addr);
        if (matcher.matches()) {
            char type = matcher.group(2).charAt(0);
            return new S7Address(DaveArea.DB, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(3)), -1, type);
        }
        throw new IllegalArgumentException("不支持的 S7 地址: " + raw);
    }

    public static int byteCount(S7Address address, DevicePoint point) {
        PointDataType dataType = point != null ? point.getDataType() : null;
        Integer stringLength = point != null ? point.getStringLength() : null;
        return byteCount(address, dataType, stringLength);
    }

    /**
     * 地址后缀给出默认宽度；点位数据类型优先，这样 DBD/REAL、LREAL 与台账类型一致。
     */
    public static int byteCount(S7Address address, PointDataType dataType, Integer stringLength) {
        if (address.bit() || (dataType != null && dataType.isBoolType())) {
            return 1;
        }
        if (dataType == null) {
            return address.suffixBytes();
        }
        return switch (dataType) {
            case INT, UINT -> 2;
            case LONG, ULONG, REAL -> 4;
            case LREAL -> 8;
            case TEXT8 -> Math.max(1, stringLength != null && stringLength > 0 ? stringLength : address.suffixBytes());
            case BOOL, BOOL_INV -> 1;
        };
    }

    public static List<List<S7ReadSpan>> splitChunks(List<S7ReadSpan> sorted, int maxBytes) {
        List<List<S7ReadSpan>> chunks = new ArrayList<>();
        List<S7ReadSpan> current = new ArrayList<>();
        int chunkStart = 0;
        int chunkEnd = 0;
        for (S7ReadSpan span : sorted) {
            int offset = span.address().offset();
            if (current.isEmpty()) {
                current.add(span);
                chunkStart = offset;
                chunkEnd = offset + span.byteCount();
                continue;
            }
            int newEnd = Math.max(chunkEnd, offset + span.byteCount());
            if (newEnd - chunkStart <= maxBytes) {
                current.add(span);
                chunkEnd = newEnd;
                continue;
            }
            chunks.add(current);
            current = new ArrayList<>();
            current.add(span);
            chunkStart = offset;
            chunkEnd = offset + span.byteCount();
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private static S7Address bitAddress(DaveArea area, int areaNumber, String offsetText, String bitText) {
        int bit = Integer.parseInt(bitText);
        if (bit < 0 || bit > 7) {
            throw new IllegalArgumentException("S7 位号超出 0-7: " + bitText);
        }
        return new S7Address(area, areaNumber, Integer.parseInt(offsetText), bit, 'X');
    }

    private static DaveArea areaOf(char area) {
        return switch (area) {
            case 'I' -> DaveArea.INPUTS;
            case 'Q' -> DaveArea.OUTPUTS;
            case 'M' -> DaveArea.FLAGS;
            default -> throw new IllegalArgumentException("不支持的 S7 地址: " + area);
        };
    }
}
