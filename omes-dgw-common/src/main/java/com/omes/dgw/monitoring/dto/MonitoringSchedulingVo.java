package com.omes.dgw.monitoring.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MonitoringSchedulingVo {

    private int devicesWithSkipStreakGte3;
    private List<MonitoringPollSkipVo> topSkipStreak = new ArrayList<>();
}
