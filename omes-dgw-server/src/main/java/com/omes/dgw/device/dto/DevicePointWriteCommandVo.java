package com.omes.dgw.device.dto;

import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.enums.WriteCommandStatus;
import com.omes.dgw.protocol.modbus.ModbusByteOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "控制写入指令")
@Data
public class DevicePointWriteCommandVo {

    private String commandId;
    private String taskId;
    private Integer seqNo;
    private String deviceId;
    private String address;
    private PointDataType dataType;
    private BigDecimal scaleFactor;
    private ModbusByteOrder byteOrder;
    private Object value;
    private WriteCommandStatus status;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime executedTime;
}
