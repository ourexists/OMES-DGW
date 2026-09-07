package com.omes.dgw.protocol.modbus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * Modbus 寄存器区：PLC4X 区前缀、惯例号/PDU 换算、ModScan 扫描限制。
 */
@Getter
public enum ModbusRegisterArea {

    COIL("coil", "线圈 (0x01)", 0, 2000, true),
    DISCRETE_INPUT("discrete-input", "离散输入 (0x02)", 10_000, 2000, false),
    HOLDING_REGISTER("holding-register", "保持寄存器 (0x03)", 40_000, 125, true),
    INPUT_REGISTER("input-register", "输入寄存器 (0x04)", 30_000, 125, false);

    @JsonValue
    private final String code;

    private final String label;

    private final int legacyBase;

    private final int pduMaxQuantity;

    /** 是否支持 Modbus 写（线圈/保持寄存器可写，离散输入/输入寄存器只读）。 */
    private final boolean writable;

    ModbusRegisterArea(String code, String label, int legacyBase, int pduMaxQuantity, boolean writable) {
        this.code = code;
        this.label = label;
        this.legacyBase = legacyBase;
        this.pduMaxQuantity = pduMaxQuantity;
        this.writable = writable;
    }

    /** PLC4X 区前缀，与 {@link #getCode()} 相同。 */
    public String plcAreaPrefix() {
        return code;
    }

    /** 区基址（30000 / 40000 / 10000 / 0）。 */
    public int areaBase() {
        return legacyBase;
    }

    /** 该区首寄存器惯例号（4x=40001、3x=30001；线圈/离散为 1）。 */
    public int pduOriginLegacy() {
        return legacyBase;
    }

    public boolean isBitArea() {
        return this == COIL || this == DISCRETE_INPUT;
    }

    public int maxScanQuantity() {
        return isBitArea() ? 8_000 : 2_000;
    }

    public String toPlc4xArea() {
        return code;
    }

    /** @param pduOffset 区内 0 基 PDU 偏移（与 {@link ModbusAddressHelper} 惯例号规则一致） */
    public String toLegacyAddress(int pduOffset) {
        return ModbusAddressHelper.legacyAddressFromPduOffset(this, pduOffset);
    }

    public static ModbusRegisterArea fromLegacyNumber(int legacy) {
        for (ModbusRegisterArea value : ModbusRegisterArea.values()) {
            if (legacy >= value.getLegacyBase() && legacy <= value.getLegacyBase() + 9999) {
                return value;
            }
        }
        return null;
    }

    public static ModbusRegisterArea fromPlcArea(String area) {
        if (area == null) {
            return null;
        }
        return switch (area.toLowerCase()) {
            case "input-register" -> INPUT_REGISTER;
            case "holding-register" -> HOLDING_REGISTER;
            case "coil" -> COIL;
            case "discrete-input" -> DISCRETE_INPUT;
            default -> null;
        };
    }

    @JsonCreator
    public static ModbusRegisterArea fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (ModbusRegisterArea area : values()) {
            if (area.code.equalsIgnoreCase(value) || area.name().equalsIgnoreCase(value)) {
                return area;
            }
        }
        throw new IllegalArgumentException("不支持的 Modbus 寄存器区: " + value);
    }
}
