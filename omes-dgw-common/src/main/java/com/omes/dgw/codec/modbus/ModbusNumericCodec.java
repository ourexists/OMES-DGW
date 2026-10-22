package com.omes.dgw.codec.modbus;

import com.omes.dgw.device.entity.DevicePoint;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.ValueNormalizer;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Modbus 连续寄存器块解码多字类型（整型/浮点/字符串等）。
 */
public final class ModbusNumericCodec {

    /** 未配置时的默认字符串长度（字符数） */
    public static final int DEFAULT_STRING_LENGTH = 2;

    private ModbusNumericCodec() {
    }

    public static boolean isMultiRegister(PointDataType dataType) {
        return registerCount(dataType, null) > 1;
    }

    public static boolean isMultiRegister(DevicePoint point) {
        return registerCount(point) > 1;
    }

    public static int registerCount(DevicePoint point) {
        if (point == null) {
            return 1;
        }
        return registerCount(point.getDataType(), point.getStringLength());
    }

    public static int registerCount(PointDataType dataType, Integer stringLength) {
        if (dataType == null) {
            return 1;
        }
        return switch (dataType) {
            case BOOL, BOOL_INV, INT, UINT -> 1;
            case REAL, LONG, ULONG -> 2;
            case LREAL -> 4;
            case TEXT8 -> registersForText8(stringLength);
        };
    }

    /** Text8：每字符 1 字节，每寄存器 2 字符 */
    public static int registersForText8(Integer charLength) {
        int len = resolveStringLength(charLength);
        return (len + 1) / 2;
    }

    public static ModbusStringCharset resolveStringCharset(PointDataType dataType) {
        return ModbusStringCharset.CHAR8;
    }

    public static int resolveStringLength(Integer configured) {
        return configured != null && configured > 0 ? configured : DEFAULT_STRING_LENGTH;
    }

    public static ModbusByteOrder resolve(ModbusByteOrder pointOrder, ModbusByteOrder deviceDefault) {
        if (pointOrder != null) {
            return pointOrder;
        }
        return deviceDefault != null ? deviceDefault : ModbusByteOrder.CDAB;
    }

    public static Object decodeFromBlock(
            Object block,
            int startIndex,
            DevicePoint point,
            ModbusByteOrder deviceByteOrder
    ) {
        if (point == null) {
            return null;
        }
        ModbusByteOrder order = resolve(point.getByteOrder(), deviceByteOrder);
        return decodeFromBlock(
                block,
                startIndex,
                point.getDataType(),
                order,
                point.getStringLength()
        );
    }

    public static Object decodeFromBlock(
            Object block,
            int startIndex,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength
    ) {
        int count = registerCount(dataType, stringLength);
        Object precomposed = tryDecodePrecomposedBlock(block, startIndex, dataType, byteOrder, stringLength, count);
        if (precomposed != null) {
            return precomposed;
        }
        int[] regs = new int[count];
        for (int i = 0; i < count; i++) {
            Object element = getElement(block, startIndex + i);
            if (element == null) {
                return null;
            }
            regs[i] = toUInt16(element);
        }
        return decodeRegisters(regs, dataType, byteOrder, stringLength);
    }

    /**
     * 仅处理「整块即一个标量」的 PLC4X 响应（如点读返回单个 Float/Double）。
     * 块读 {@code [n]} 须走按寄存器下标组字；不可用 Float/Double 列表 + {@code startIndex} 推算，否则会错位。
     */
    private static Object tryDecodePrecomposedBlock(
            Object block,
            int startIndex,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength,
            int regCount
    ) {
        if (block == null || dataType == null || regCount <= 0 || startIndex != 0) {
            return null;
        }
        if (block instanceof List<?>) {
            return null;
        }
        if (block instanceof Float f && dataType == PointDataType.REAL) {
            return f;
        }
        if (block instanceof Double d) {
            return switch (dataType) {
                case REAL -> d.floatValue();
                case LREAL -> d;
                default -> null;
            };
        }
        if (block instanceof Number n && regCount == registerCount(dataType, stringLength)) {
            return decodeScalarNumber(n, dataType, byteOrder);
        }
        return null;
    }

    /**
     * PLC4X 已组好的标量：整型按位解释（REAL 用 intBitsToFloat），勿用 {@link Number#floatValue()} 当工程量。
     */
    private static Object decodeScalarNumber(Number n, PointDataType dataType, ModbusByteOrder byteOrder) {
        return switch (dataType) {
            case REAL -> n instanceof Float f
                    ? f
                    : Float.intBitsToFloat(n.intValue());
            case LREAL -> n instanceof Double d
                    ? d
                    : Double.longBitsToDouble(n.longValue());
            case INT -> (short) n.intValue();
            case UINT -> n.intValue() & 0xFFFF;
            case LONG -> n.intValue();
            case ULONG -> n.longValue() & 0xFFFFFFFFL;
            case BOOL, BOOL_INV -> decodeBoolWire(n.intValue() != 0, dataType);
            case TEXT8 -> null;
        };
    }

    public static Object decodeRegisters(int[] registers, PointDataType dataType, ModbusByteOrder byteOrder) {
        return decodeRegisters(registers, dataType, byteOrder, null);
    }

    public static Object decodeRegisters(
            int[] registers,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength
    ) {
        if (registers == null || dataType == null || registers.length == 0) {
            return null;
        }
        ModbusByteOrder order = byteOrder != null ? byteOrder : ModbusByteOrder.CDAB;
        int reg0 = registers[0] & 0xFFFF;
        return switch (dataType) {
            case BOOL, BOOL_INV -> decodeBoolWire(reg0 != 0, dataType);
            case INT -> (short) reg0;
            case UINT -> reg0;
            case REAL -> {
                if (registers.length < 2) {
                    yield null;
                }
                yield Float.intBitsToFloat(composeInt32(registers[0], registers[1], order));
            }
            case LONG -> {
                if (registers.length < 2) {
                    yield null;
                }
                yield composeInt32(registers[0], registers[1], order);
            }
            case ULONG -> {
                if (registers.length < 2) {
                    yield null;
                }
                yield Integer.toUnsignedLong(composeInt32(registers[0], registers[1], order));
            }
            case LREAL -> {
                if (registers.length < 4) {
                    yield null;
                }
                yield Double.longBitsToDouble(composeInt64(registers, order));
            }
            case TEXT8 -> decodeString(
                    registers,
                    resolveStringCharset(dataType),
                    resolveStringLength(stringLength)
            );
        };
    }

    /**
     * 8 位字符串：寄存器内低字节在前、高字节在后。
     */
    public static String decodeString(int[] registers, ModbusStringCharset charset, int charLength) {
        if (registers == null || registers.length == 0 || charLength <= 0) {
            return "";
        }
        ModbusStringCharset resolved = charset != null ? charset : ModbusStringCharset.CHAR8;
        StringBuilder sb = new StringBuilder(charLength);
        if (resolved == ModbusStringCharset.CHAR16) {
            for (int reg : registers) {
                if (sb.length() >= charLength) {
                    break;
                }
                char ch = (char) (reg & 0xFFFF);
                if (ch == 0) {
                    break;
                }
                sb.append(ch);
            }
            return sb.length() > charLength ? sb.substring(0, charLength) : sb.toString();
        }
        for (int reg : registers) {
            int lo = reg & 0xFF;
            int hi = (reg >> 8) & 0xFF;
            if (lo != 0 && sb.length() < charLength) {
                sb.append((char) lo);
            }
            if (hi != 0 && sb.length() < charLength) {
                sb.append((char) hi);
            }
            if (sb.length() >= charLength) {
                break;
            }
        }
        return sb.length() > charLength ? sb.substring(0, charLength) : sb.toString();
    }

    /**
     * 32 位按字节 A(最高)…D(最低) 命名：reg0=AB、reg1=CD。
     * ABCD：w0 高字、w1 低字；CDAB：高低字互换；BADC：字内字节互换；DCBA：字互换且字内字节互换。
     */
    static int composeInt32(int reg0, int reg1, ModbusByteOrder order) {
        int w0 = reg0 & 0xFFFF;
        int w1 = reg1 & 0xFFFF;
        return switch (order) {
            case ABCD -> (w0 << 16) | w1;
            case CDAB -> (w1 << 16) | w0;
            case BADC -> (swapBytes16(w0) << 16) | swapBytes16(w1);
            case DCBA -> (swapBytes16(w1) << 16) | swapBytes16(w0);
        };
    }

    /**
     * 64 位按字节 A(最高)…H(最低) 命名：reg0=AB、reg1=CD、reg2=EF、reg3=GH。
     * 与 {@link #composeInt32} 规则一致：ABCD 顺序组字；CDAB 高低 32 位互换；BADC 字内字节互换；DCBA 二者兼有。
     * LREAL 经 {@link Double#longBitsToDouble(long)} / {@link Double#doubleToLongBits(double)} 与线侧寄存器互转。
     */
    private static long composeInt64(int[] regs, ModbusByteOrder order) {
        if (regs == null || regs.length < 4) {
            return 0L;
        }
        int[] words = new int[4];
        for (int i = 0; i < 4; i++) {
            words[i] = regs[i] & 0xFFFF;
        }
        int[] ordered = switch (order) {
            case ABCD -> words;
            case CDAB -> new int[]{words[1], words[0], words[3], words[2]};
            case BADC -> new int[]{
                    swapBytes16(words[0]), swapBytes16(words[1]),
                    swapBytes16(words[2]), swapBytes16(words[3])
            };
            case DCBA -> new int[]{
                    swapBytes16(words[3]), swapBytes16(words[2]),
                    swapBytes16(words[1]), swapBytes16(words[0])
            };
        };
        long hi = ((long) ordered[0] << 16) | (ordered[1] & 0xFFFFL);
        long lo = ((long) ordered[2] << 16) | (ordered[3] & 0xFFFFL);
        return (hi << 32) | (lo & 0xFFFFFFFFL);
    }

    private static int swapBytes16(int word) {
        return ((word & 0xFF) << 8) | ((word >> 8) & 0xFF);
    }

    public static Boolean decodeBool(Object wireValue, PointDataType dataType) {
        if (dataType == null || !dataType.isBoolType()) {
            return null;
        }
        return decodeBoolWire(toBoolWire(wireValue), dataType);
    }

    private static boolean toBoolWire(Object wireValue) {
        if (wireValue instanceof Boolean b) {
            return b;
        }
        if (wireValue instanceof Number n) {
            return n.intValue() != 0;
        }
        if (wireValue == null) {
            return false;
        }
        wireValue = ValueNormalizer.toJava(wireValue);
        if (wireValue instanceof Boolean b) {
            return b;
        }
        if (wireValue instanceof Number n) {
            return n.intValue() != 0;
        }
        String str = wireValue.toString().trim().toLowerCase();
        return "true".equals(str) || "1".equals(str) || "on".equals(str);
    }

    private static Boolean decodeBoolWire(boolean wire, PointDataType dataType) {
        return dataType.isBoolInverted() != wire;
    }

    /** 工程布尔值转为下发到设备的线侧值（写线圈/寄存器前调用）。 */
    public static Boolean encodeBoolWire(Object engineeringValue, PointDataType dataType) {
        if (dataType == null || !dataType.isBoolType()) {
            return null;
        }
        boolean eng = toBoolWire(engineeringValue);
        return dataType.isBoolInverted() != eng;
    }

    public static ModbusPlcWritePayload preparePlcWrite(
            DevicePoint point,
            Object rawValue,
            ModbusByteOrder deviceOrder
    ) {
        if (point == null || point.getDataType() == null || rawValue == null) {
            return null;
        }
        return preparePlcWriteForAddress(
                point.getSetpointAddress(),
                point.getDataType(),
                rawValue,
                point.getByteOrder(),
                deviceOrder,
                point.getStringLength());
    }

    /**
     * 按地址与数据类型组 PLC 写入（控制接口等无采集点位配置时使用）。
     */
    public static ModbusPlcWritePayload preparePlcWriteForAddress(
            String address,
            PointDataType dataType,
            Object rawValue,
            ModbusByteOrder byteOrder,
            ModbusByteOrder deviceOrder,
            Integer stringLength
    ) {
        if (address == null || address.isBlank() || dataType == null || rawValue == null) {
            return null;
        }
        ModbusByteOrder order = resolve(byteOrder, deviceOrder);
        int regCount = registerCount(dataType, stringLength);
        String plcAddress = ModbusAddressHelper.toPlc4xWriteAddress(address.trim(), dataType, regCount);
        String area = extractModbusArea(address);
        Object plcValue = encodePlcWriteValue(rawValue, dataType, order, stringLength, area);
        if (plcValue == null) {
            return null;
        }
        return new ModbusPlcWritePayload(plcAddress, plcValue);
    }

    /**
     * 寄存器区写入值：与轮询块读对称，经 {@link #encodeToRegisters} / {@link #decomposeInt32} 按字母序拆成寄存器列表。
     */
    public static Object encodePlcWriteValue(
            Object rawValue,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength,
            String modbusArea
    ) {
        if (rawValue == null || dataType == null) {
            return null;
        }
        rawValue = ValueNormalizer.toJava(rawValue);
        ModbusByteOrder order = byteOrder != null ? byteOrder : ModbusByteOrder.CDAB;
        if (isBitArea(modbusArea)) {
            if (dataType.isBoolType()) {
                return toBoolWire(rawValue);
            }
            return rawValue;
        }
        if (dataType.isModbusStringType()) {
            return toRegisterList(encodeStringRegisters(rawValue.toString(), dataType, stringLength));
        }
        int[] regs = encodeToRegisters(rawValue, dataType, order, stringLength);
        if (regs == null || regs.length == 0) {
            return null;
        }
        if (regs.length == 1) {
            return registerWordToPlcScalar(regs[0], dataType);
        }
        return regs;
    }

    /** 16 位寄存器字 → PLC4X 标量（INT 等有符号类型须用 short，避免 65535 越界）。 */
    static Object registerWordToPlcScalar(int reg, PointDataType dataType) {
        reg &= 0xFFFF;
        if (dataType == null) {
            return (short) reg;
        }
        return switch (dataType) {
            case INT -> (short) reg;
            case UINT -> reg;
            case BOOL, BOOL_INV -> reg != 0;
            default -> reg;
        };
    }

    static String extractModbusArea(String address) {
        if (address == null || address.isBlank()) {
            return "holding-register";
        }
        String base = ModbusAddressHelper.stripDataTypeSuffix(
                ModbusAddressHelper.convertLegacyAddress(address.trim()));
        int colon = base.indexOf(':');
        return colon > 0 ? base.substring(0, colon) : "holding-register";
    }

    public static int[] encodeToRegisters(
            Object rawValue,
            PointDataType dataType,
            ModbusByteOrder byteOrder,
            Integer stringLength
    ) {
        if (rawValue == null || dataType == null) {
            return null;
        }
        rawValue = ValueNormalizer.toJava(rawValue);
        ModbusByteOrder order = byteOrder != null ? byteOrder : ModbusByteOrder.CDAB;
        return switch (dataType) {
            case BOOL, BOOL_INV -> new int[]{toBoolWire(rawValue) ? 1 : 0};
            case INT -> new int[]{toNumber(rawValue).shortValue() & 0xFFFF};
            case UINT -> new int[]{toNumber(rawValue).intValue() & 0xFFFF};
            case REAL -> decomposeInt32(Float.floatToIntBits(toNumber(rawValue).floatValue()), order);
            case LONG -> decomposeInt32(toNumber(rawValue).intValue(), order);
            case ULONG -> decomposeInt32((int) (toNumber(rawValue).longValue() & 0xFFFFFFFFL), order);
            case LREAL -> decomposeInt64(Double.doubleToLongBits(toNumber(rawValue).doubleValue()), order);
            case TEXT8 -> encodeStringRegisters(rawValue.toString(), dataType, stringLength);
        };
    }

    public static int[] encodeStringRegisters(String text, PointDataType dataType, Integer configuredLength) {
        int charLength = resolveStringLength(configuredLength);
        String source = text != null ? text : "";
        char[] chars = new char[charLength];
        for (int i = 0; i < charLength; i++) {
            chars[i] = i < source.length() ? source.charAt(i) : 0;
        }
        int regCount = registersForText8(charLength);
        int[] regs = new int[regCount];
        for (int i = 0; i < regCount; i++) {
            int lo = (i * 2) < chars.length ? chars[i * 2] & 0xFF : 0;
            int hi = (i * 2 + 1) < chars.length ? chars[i * 2 + 1] & 0xFF : 0;
            regs[i] = lo | (hi << 8);
        }
        return regs;
    }

    private static List<Integer> toRegisterList(int[] regs) {
        List<Integer> list = new ArrayList<>(regs.length);
        for (int reg : regs) {
            list.add(reg & 0xFFFF);
        }
        return list;
    }

    /**
     * 供 {@link org.apache.plc4x.java.api.messages.PlcWriteRequest.Builder#addTagAddress} 的末参展开：
     * 单寄存器（如 INT）须为标量；多寄存器用 {@code Object[]}（{@code int[]} 作为 varargs 只会传 1 项）。
     */
    public static Object[] toPlcWriteVarargs(Object plcValue) {
        if (plcValue == null) {
            return new Object[]{null};
        }
        if (plcValue instanceof int[] ints) {
            if (ints.length <= 1) {
                Object scalar = ints.length == 0 ? null : registerWordToPlcScalar(ints[0], null);
                return new Object[]{scalar};
            }
            Object[] spread = new Object[ints.length];
            for (int i = 0; i < ints.length; i++) {
                spread[i] = ints[i] & 0xFFFF;
            }
            return spread;
        }
        if (plcValue instanceof List<?> list) {
            return toPlcWriteVarargs(listToRegisterArray(list));
        }
        return new Object[]{plcValue};
    }

    private static int[] listToRegisterArray(List<?> list) {
        int[] regs = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object item = list.get(i);
            regs[i] = item instanceof Number n ? n.intValue() & 0xFFFF : 0;
        }
        return regs;
    }

    static int[] decomposeInt32(int value, ModbusByteOrder order) {
        int high = (value >> 16) & 0xFFFF;
        int low = value & 0xFFFF;
        return switch (order) {
            case ABCD -> new int[]{high, low};
            case CDAB -> new int[]{low, high};
            case BADC -> new int[]{swapBytes16(high), swapBytes16(low)};
            case DCBA -> new int[]{swapBytes16(low), swapBytes16(high)};
        };
    }

    /** {@link #composeInt64(int[], ModbusByteOrder)} 的逆变换，供 LREAL 等 4 寄存器类型写入。 */
    static int[] decomposeInt64(long value, ModbusByteOrder order) {
        int[] logical = new int[]{
                (int) ((value >> 48) & 0xFFFF),
                (int) ((value >> 32) & 0xFFFF),
                (int) ((value >> 16) & 0xFFFF),
                (int) (value & 0xFFFF)
        };
        return switch (order) {
            case ABCD -> logical;
            case CDAB -> new int[]{logical[1], logical[0], logical[3], logical[2]};
            case BADC -> new int[]{
                    swapBytes16(logical[0]), swapBytes16(logical[1]),
                    swapBytes16(logical[2]), swapBytes16(logical[3])
            };
            case DCBA -> new int[]{
                    swapBytes16(logical[3]), swapBytes16(logical[2]),
                    swapBytes16(logical[1]), swapBytes16(logical[0])
            };
        };
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        return new java.math.BigDecimal(value.toString().trim());
    }

    private static boolean isBitArea(String area) {
        return "coil".equals(area) || "discrete-input".equals(area);
    }

    static int toUInt16(Object value) {
        if (value instanceof Number number) {
            return number.intValue() & 0xFFFF;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        value = ValueNormalizer.toJava(value);
        if (value instanceof Number number) {
            return number.intValue() & 0xFFFF;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        return Integer.parseInt(value.toString().trim()) & 0xFFFF;
    }

    /**
     * 从 PLC4X 块读结果中取元素：支持 {@link List}、数组；标量仅 index=0 有效。
     */
    public static Object getElement(Object block, int index) {
        if (block == null || index < 0) {
            return null;
        }
        if (block instanceof List<?> list) {
            return index < list.size() ? list.get(index) : null;
        }
        if (block.getClass().isArray()) {
            return index < Array.getLength(block) ? Array.get(block, index) : null;
        }
        return index == 0 ? block : null;
    }
}
