package com.omes.dgw.modscan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@Schema(description = "Modbus 寄存器区选项")
public class ModscanRegisterAreaOptionVo {

    private String code;
    private String label;
}
