/*
 * Copyright (c) 2025. created by ourexists.https://gitee.com/ourexists
 */

package com.omes.dgw.protocol;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class ProtocolConnect {

    protected String id;

    /** 设备 IP 或主机名 */
    private String uri;

    private String protocol;

    private String params;

    private Boolean enabled;

    /** 采集周期（秒），固定速率调度间隔 */
    private Integer collectIntervalSec;
}
