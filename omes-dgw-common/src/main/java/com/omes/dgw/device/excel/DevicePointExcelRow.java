package com.omes.dgw.device.excel;

import cn.afterturn.easypoi.excel.annotation.Excel;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 点位导入导出 Excel 行（枚举字段使用 label 文本）
 */
@Data
public class DevicePointExcelRow {

    @Excel(name = "*设备编码", width = 18, orderNum = "0")
    private String deviceCode;

    @Excel(name = "*点位编码", width = 18, orderNum = "1")
    private String code;

    @Excel(name = "*点位名称", width = 20, orderNum = "2")
    private String name;

    @Excel(name = "*读写类型", width = 12, orderNum = "3")
    private String accessTypeLabel;

    @Excel(name = "实际值点位", width = 22, orderNum = "4")
    private String actualAddress;

    @Excel(name = "设定值点位", width = 22, orderNum = "5")
    private String setpointAddress;

    @Excel(name = "*数据类型", width = 14, orderNum = "6")
    private String dataTypeLabel;

    @Excel(name = "字节序", width = 12, orderNum = "7")
    private String byteOrder;

    @Excel(name = "缩放系数", width = 12, orderNum = "8")
    private BigDecimal scaleFactor;

    @Excel(name = "工程下限", width = 12, orderNum = "9")
    private BigDecimal engValueMin;

    @Excel(name = "工程上限", width = 12, orderNum = "10")
    private BigDecimal engValueMax;

    @Excel(name = "工程单位", width = 10, orderNum = "11")
    private String engUnit;

    @Excel(name = "小数位", width = 10, orderNum = "12")
    private Integer decimalPlaces;

    /** 填写 是/否（Packed BCD：采集 0x1234→1234，写入 1234→0x1234） */
    @Excel(name = "BCD转码", width = 12, orderNum = "13")
    private String bcdConvert;

    /** 填写 A/B/C/D/BA/DC（别名 AB/CD） */
    @Excel(name = "位域", width = 12, orderNum = "14")
    private String bitField;
}
