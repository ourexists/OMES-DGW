package com.omes.dgw.device.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.omes.dgw.common.BaseEntity;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.enums.WriteCommandStatus;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备控制写入指令。
 * 入参可通过点位 code 解析采集配置，或直接指定地址与数据类型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("T_DEVICE_POINT_WRITE_COMMAND")
public class DevicePointWriteCommand extends BaseEntity {

    private String taskId;

    /** 任务内执行序号，从 1 递增 */
    private Integer seqNo;

    private String deviceId;

    /** 协议写入地址 */
    private String address;

    private PointDataType dataType;

    private BigDecimal scaleFactor;

    /** Modbus 多寄存器字节序（ABCD/BADC/CDAB/DCBA） */
    private ModbusByteOrder byteOrder;

    /** 写入值（JSON 字符串） */
    private String writeValue;

    private WriteCommandStatus status;

    private String errorMessage;

    @TableField("EXECUTED_TIME")
    private LocalDateTime executedTime;
}
