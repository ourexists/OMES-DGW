package com.omes.dgw.device.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.omes.dgw.common.BaseEntity;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import com.omes.dgw.device.dto.DeviceDto;
import com.omes.dgw.device.dto.DeviceVo;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.codec.fins.FinsTcpConnectionUrl;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.codec.opcua.OpcUaConnectionUrl;
import com.omes.dgw.codec.s7.S7ConnectParams;
import com.omes.dgw.codec.s7.S7TcpConnectionUrl;
import com.omes.dgw.codec.slmp.SlmpTcpConnectionUrl;
import com.omes.dgw.codec.modbus.ModbusTcpConnectionUrl;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 设备
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "T_DEVICE", autoResultMap = true)
public class Device extends BaseEntity {

    /** 名称 */
    private String name;

    /** 编码 */
    private String code;

    /** 通信协议 */
    private DeviceProtocol protocol;

    /** 所属分组（设备分类编号） */
    private String categoryCode;

    /** IP 地址 */
    private String ipAddress;

    /** 端口号 */
    private Integer port;

    /** 默认采集周期（秒） */
    private Integer collectIntervalSec;

    /** 连接超时时间（毫秒） */
    private Integer connectTimeoutMs;

    /** 是否启用（启用时启动协议轮询） */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    /** 采集配置（含点位列表，JSON 存入 COLLECT_CONFIG） */
    @TableField(value = "COLLECT_CONFIG", typeHandler = JacksonTypeHandler.class)
    private DeviceCollectConfig collectConfig;

    public DeviceVo toVo(String categoryName) {
        DeviceVo vo = new DeviceVo();
        BeanUtils.copyProperties(this, vo);
        vo.setCategoryName(categoryName);
        if (protocol != null) {
            vo.setProtocolLabel(protocol.getLabel());
        }
        vo.setUnitId(resolveUnitId(this));
        if (protocol == DeviceProtocol.MODBUS_TCP) {
            ModbusTcpConnectionUrl.Parts parts = ModbusTcpConnectionUrl.fromDevice(
                    ipAddress, port, connectTimeoutMs);
            vo.setConnectionUrl(ModbusTcpConnectionUrl.build(
                    parts.host(), parts.port(), parts.unitId(), parts.requestTimeoutMs()));
        } else if (protocol == DeviceProtocol.FINS_TCP) {
            DeviceCollectConfig config = DevicePointConfigSupport.normalize(collectConfig);
            FinsTcpConnectionUrl.Parts parts = FinsTcpConnectionUrl.fromDevice(
                    ipAddress, port, connectTimeoutMs, config.getFinsLocalNode(), config.getFinsRemoteNode());
            vo.setConnectionUrl(FinsTcpConnectionUrl.build(
                    parts.host(), parts.port(), parts.localNode(), parts.remoteNode(), parts.requestTimeoutMs()));
        } else if (protocol == DeviceProtocol.SLMP_TCP) {
            DeviceCollectConfig config = DevicePointConfigSupport.normalize(collectConfig);
            SlmpTcpConnectionUrl.Parts parts = SlmpTcpConnectionUrl.fromDevice(
                    ipAddress, port, connectTimeoutMs, config.getSlmpNetworkNo(), config.getSlmpStationNo());
            vo.setConnectionUrl(SlmpTcpConnectionUrl.build(
                    parts.host(), parts.port(), parts.networkNo(), parts.stationNo(), parts.requestTimeoutMs()));
        } else if (protocol == DeviceProtocol.S7_TCP) {
            DeviceCollectConfig config = DevicePointConfigSupport.normalize(collectConfig);
            int rack = config.getS7Rack() != null ? config.getS7Rack() : S7ConnectParams.DEFAULT_RACK;
            int slot = config.getS7Slot() != null ? config.getS7Slot() : S7ConnectParams.DEFAULT_SLOT;
            S7TcpConnectionUrl.Parts parts = S7TcpConnectionUrl.fromDevice(
                    ipAddress, port, connectTimeoutMs, rack, slot);
            vo.setConnectionUrl(S7TcpConnectionUrl.build(parts));
            vo.setS7Rack(rack);
            vo.setS7Slot(slot);
        } else if (protocol == DeviceProtocol.OPC_UA) {
            DeviceCollectConfig config = DevicePointConfigSupport.normalize(collectConfig);
            boolean discovery = config.getOpcuaDiscovery() == null || config.getOpcuaDiscovery();
            String policy = OpcUaConnectionUrl.normalizePolicy(config.getOpcuaSecurityPolicy());
            OpcUaConnectionUrl.Parts parts = new OpcUaConnectionUrl.Parts(
                    ipAddress,
                    port != null ? port : OpcUaConnectionUrl.DEFAULT_PORT,
                    discovery,
                    config.getOpcuaUsername(),
                    config.getOpcuaPassword(),
                    policy,
                    connectTimeoutMs != null ? connectTimeoutMs : 10_000);
            vo.setConnectionUrl(OpcUaConnectionUrl.buildMasked(parts));
            vo.setOpcuaDiscovery(discovery);
            vo.setOpcuaSecurityPolicy(policy);
            vo.setOpcuaUsername(config.getOpcuaUsername());
            vo.setOpcuaPassword(config.getOpcuaPassword());
        }
        return vo;
    }

    /**
     * 转为实时状态快照的设备头信息（不含在线状态、eventId、点位数据等运行时字段）。
     */
    public DeviceRealtimeObj toRealtimeVo() {
        DeviceRealtimeObj vo = new DeviceRealtimeObj();
        mergeIntoRealtimeVo(vo);
        vo.setLastUpdateTime(LocalDateTime.now());
        return vo;
    }

    /**
     * 将台账静态字段写入已有实时快照，不覆盖 online、点位、eventId、连接状态等运行时字段。
     */
    public void mergeIntoRealtimeVo(DeviceRealtimeObj vo) {
        if (vo == null) {
            return;
        }
        vo.setDeviceId(getId());
        vo.setDeviceCode(getCode());
        vo.setDeviceName(getName());
        vo.setProtocol(getProtocol());
        vo.setIpAddress(getIpAddress());
        vo.setPort(getPort());
        vo.setCollectIntervalSec(getCollectIntervalSec());
        vo.setConnectTimeoutMs(getConnectTimeoutMs());
        vo.setCollectConfig(DevicePointConfigSupport.normalize(getCollectConfig()));
    }

    public static List<DeviceVo> toVoList(List<Device> entities, Map<String, String> categoryNames) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(device -> device.toVo(
                        categoryNames != null ? categoryNames.get(device.getCategoryCode()) : null))
                .toList();
    }

    public static Device fromDto(DeviceDto dto) {
        Device entity = new Device();
        entity.applyDto(dto);
        entity.setId(null);
        return entity;
    }

    public void applyDto(DeviceDto dto) {
        BeanUtils.copyProperties(dto, this);
        if (!StringUtils.hasText(this.categoryCode)) {
            this.categoryCode = null;
        } else {
            this.categoryCode = this.categoryCode.trim();
        }
        if (this.enabled == null) {
            this.enabled = false;
        }
        if (this.protocol == DeviceProtocol.MODBUS_TCP) {
            if (this.collectConfig != null) {
                this.collectConfig.setReusePollingConnection(null);
            }
        } else if (dto.getReusePollingConnection() != null) {
            DeviceCollectConfig config = this.collectConfig != null
                    ? this.collectConfig
                    : new DeviceCollectConfig();
            config.setReusePollingConnection(dto.getReusePollingConnection());
            this.collectConfig = DevicePointConfigSupport.normalize(config, this.protocol);
        }
        applyUnitIdFromDto(dto);
        applyS7FromDto(dto);
        applyOpcUaFromDto(dto);
    }

    private void applyOpcUaFromDto(DeviceDto dto) {
        if (this.protocol != DeviceProtocol.OPC_UA) {
            return;
        }
        DeviceCollectConfig config = this.collectConfig != null
                ? this.collectConfig
                : new DeviceCollectConfig();
        config.setOpcuaDiscovery(dto.getOpcuaDiscovery() == null || dto.getOpcuaDiscovery());
        config.setOpcuaSecurityPolicy(OpcUaConnectionUrl.normalizePolicy(dto.getOpcuaSecurityPolicy()));
        config.setOpcuaUsername(blankToNull(dto.getOpcuaUsername()));
        config.setOpcuaPassword(blankToNull(dto.getOpcuaPassword()));
        try {
            OpcUaConnectionUrl.checkCredential("用户名", config.getOpcuaUsername());
            OpcUaConnectionUrl.checkCredential("密码", config.getOpcuaPassword());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        this.collectConfig = DevicePointConfigSupport.normalize(config, this.protocol);
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void applyS7FromDto(DeviceDto dto) {
        if (this.protocol != DeviceProtocol.S7_TCP) {
            return;
        }
        DeviceCollectConfig config = this.collectConfig != null
                ? this.collectConfig
                : new DeviceCollectConfig();
        config.setS7Rack(dto.getS7Rack() != null ? dto.getS7Rack() : S7ConnectParams.DEFAULT_RACK);
        config.setS7Slot(dto.getS7Slot() != null ? dto.getS7Slot() : S7ConnectParams.DEFAULT_SLOT);
        this.collectConfig = DevicePointConfigSupport.normalize(config, this.protocol);
    }

    private void applyUnitIdFromDto(DeviceDto dto) {
        if (dto.getUnitId() == null || this.protocol == null) {
            return;
        }
        DeviceCollectConfig config = this.collectConfig != null
                ? this.collectConfig
                : new DeviceCollectConfig();
        switch (this.protocol) {
            case FINS_TCP -> config.setFinsRemoteNode(dto.getUnitId());
            case SLMP_TCP -> config.setSlmpStationNo(dto.getUnitId());
            default -> {
            }
        }
        this.collectConfig = DevicePointConfigSupport.normalize(config, this.protocol);
    }

    private static Integer resolveUnitId(Device device) {
        if (device == null || device.getProtocol() == null) {
            return null;
        }
        DeviceCollectConfig config = DevicePointConfigSupport.normalize(device.getCollectConfig());
        return switch (device.getProtocol()) {
            case FINS_TCP -> config.getFinsRemoteNode();
            case SLMP_TCP -> config.getSlmpStationNo();
            default -> null;
        };
    }
}
