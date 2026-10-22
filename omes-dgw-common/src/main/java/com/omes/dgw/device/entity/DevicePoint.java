package com.omes.dgw.device.entity;

import com.omes.dgw.common.BaseEntity;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 设备点位（运行时模型，配置持久化在设备的 COLLECT_CONFIG 中）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DevicePoint extends BaseEntity {

    /** 所属设备 ID */
    private String deviceId;

    /** 点位编码 */
    private String code;

    /** 点位名称 */
    private String name;

    /** 读写控制类型 */
    private PointAccessType accessType;

    /** 实际值点位（采集地址；只读/读写） */
    private String actualAddress;

    /** 设定值点位（写入地址；只写/读写） */
    private String setpointAddress;

    /** 数据类型 */
    private PointDataType dataType;

    /** Modbus 多寄存器字节序（浮点/32 位等） */
    private ModbusByteOrder byteOrder;

    /** 字符串长度（字符数，WinCC 文本变量长度） */
    private Integer stringLength;

    /** 缩放系数 */
    private BigDecimal scaleFactor;

    /** 工程值下限（可选；不填则读写不做下限限制） */
    private BigDecimal engValueMin;

    /** 工程值上限（可选；不填则读写不做上限限制） */
    private BigDecimal engValueMax;

    private String engUnit;

    /** 小数位 */
    private Integer decimalPlaces;

    /** BCD 转码（Packed BCD：采集 0x1234→1234，写入 1234→0x1234） */
    private Boolean bcdConvert;

    /** 位域：A/B/C/D/BA/DC */
    private String bitField;

    /** 位域起始位（由 bitField 推导，兼容旧配置） */
    private Integer bitOffset;

    /** 位域长度（由 bitField 推导，兼容旧配置） */
    private Integer bitLength;

    /** 采集用地址（实际值点位） */
    public String getAddress() {
        return actualAddress;
    }

    public void setAddress(String address) {
        this.actualAddress = address;
    }
}
