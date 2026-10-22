package com.omes.dgw.device.online;

import lombok.Getter;

/**
 * 设备在线态时段状态，与 {@code t_equip_record_online.state} 一致。
 */
@Getter
public enum DeviceOnlineState {

    OFFLINE(0),
    ONLINE(1);

    private final int code;

    DeviceOnlineState(int code) {
        this.code = code;
    }

    public static DeviceOnlineState fromOnline(boolean online) {
        return online ? ONLINE : OFFLINE;
    }
}
