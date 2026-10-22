package com.omes.dgw.protocol;

import com.omes.dgw.device.connect.ProtocolConnect;
import com.omes.dgw.device.connect.ProtocolWriteRequest;
import com.omes.dgw.device.connect.ProtocolWriteResult;
import com.omes.dgw.device.dto.DeviceConnectionTestVo;

public interface ProtocolManager {

    default boolean supports(String protocol) {
        return protocol() != null && protocol().equals(protocol);
    }

    boolean start(ProtocolConnect connect);

    boolean stop(String connectId);

    void stopAll();

    String protocol();

    /**
     * 向设备写入控制值。
     *
     * @param connectId 网关连接 ID
     * @param address   PLC/设备 可写地址
     * @param value     写入值
     * @return 是否写入成功
     */
    default ProtocolWriteResult write(String connectId, ProtocolWriteRequest request) {
        if (request == null || !org.springframework.util.StringUtils.hasText(request.address())
                || request.value() == null) {
            return ProtocolWriteResult.fail("写入参数无效");
        }
        return ProtocolWriteResult.fail("协议 " + protocol() + " 未实现写入");
    }

    /**
     * 探测连接是否可用（不写实时缓存、不触发采集）。
     */
    default DeviceConnectionTestVo testConnection(ProtocolConnect connect) {
        return DeviceConnectionTestVo.fail("协议 " + protocol() + " 暂不支持连接测试");
    }


}
