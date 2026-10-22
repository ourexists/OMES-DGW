package com.omes.dgw.protocol;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用关闭时停止全部协议轮询任务。
 */
@Slf4j
@Component
public class ProtocolManagerRunner {

    private final List<ProtocolManager> protocolManagers;

    public ProtocolManagerRunner(List<ProtocolManager> protocolManagers) {
        this.protocolManagers = protocolManagers;
    }

    @PreDestroy
    public void destroy() {
        for (ProtocolManager manager : protocolManagers) {
            manager.stopAll();
        }
    }

}
