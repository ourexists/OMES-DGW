package com.omes.dgw.monitoring.dto;

import lombok.Data;

@Data
public class MonitoringAlertVo {

    private String code;
    private String severity;
    private String message;
    private String target;
}
