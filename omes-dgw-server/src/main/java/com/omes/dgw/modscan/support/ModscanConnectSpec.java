package com.omes.dgw.modscan.support;

/**
 * ModScan 连接参数（PLC4X 连接串与读超时）。
 */
public record ModscanConnectSpec(String connectionUrl, int requestTimeoutMs) {
}
