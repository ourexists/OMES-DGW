package com.omes.dgw.device.excel;

import cn.afterturn.easypoi.excel.annotation.Excel;
import lombok.Data;

/**
 * 设备导入导出 Excel 行（枚举字段使用 label 文本）
 */
@Data
public class DeviceExcelRow {

    @Excel(name = "*设备编码", width = 18, orderNum = "0")
    private String code;

    @Excel(name = "*设备名称", width = 20, orderNum = "1")
    private String name;

    @Excel(name = "*通信协议", width = 16, orderNum = "2")
    private String protocolLabel;

    @Excel(name = "分组编号", width = 14, orderNum = "3")
    private String categoryCode;

    @Excel(name = "分组名称", width = 16, orderNum = "4")
    private String categoryName;

    @Excel(name = "*IP地址", width = 16, orderNum = "5")
    private String ipAddress;

    @Excel(name = "*端口号", width = 10, orderNum = "6")
    private Integer port;

    @Excel(name = "*采集周期(秒)", width = 14, orderNum = "7")
    private Integer collectIntervalSec;

    @Excel(name = "*连接超时(毫秒)", width = 16, orderNum = "8")
    private Integer connectTimeoutMs;

    @Excel(name = "是否共享查询线程", width = 20, orderNum = "9")
    private String reusePollingConnection;

    @Excel(name = "备注", width = 24, orderNum = "10")
    private String remark;
}
