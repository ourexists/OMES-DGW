package com.omes.dgw.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omes.dgw.common.BusinessException;
import com.omes.dgw.device.cache.BitFieldExtractor;
import com.omes.dgw.device.cache.PointValueScaler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omes.dgw.common.PageResult;
import com.omes.dgw.device.dto.DevicePointWriteBatchRequest;
import com.omes.dgw.device.dto.DevicePointWriteCommandQuery;
import com.omes.dgw.device.dto.DevicePointWriteCommandVo;
import com.omes.dgw.device.dto.DevicePointWriteItemRequest;
import com.omes.dgw.device.dto.DevicePointWriteRequest;
import com.omes.dgw.device.dto.DevicePointWriteTaskHistoryVo;
import com.omes.dgw.device.dto.DevicePointWriteTaskVo;
import com.omes.dgw.device.entity.Device;
import com.omes.dgw.device.entity.DevicePointWriteCommand;
import com.omes.dgw.device.enums.DeviceProtocol;
import com.omes.dgw.device.enums.PointAccessType;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.device.enums.WriteCommandStatus;
import com.omes.dgw.device.mapper.DeviceMapper;
import com.omes.dgw.device.mapper.DevicePointWriteCommandMapper;
import com.omes.dgw.device.model.DeviceCollectConfig;
import com.omes.dgw.device.model.DevicePointConfigItem;
import com.omes.dgw.device.service.DevicePointWriteService;
import com.omes.dgw.device.support.DevicePointConfigSupport;
import com.omes.dgw.device.write.DevicePointWriteExecutor;
import com.omes.dgw.device.connect.DeviceProtocolGateway;
import org.springframework.beans.factory.ObjectProvider;
import com.omes.dgw.device.connect.ProtocolWriteRequest;
import com.omes.dgw.device.connect.ProtocolWriteResult;
import com.omes.dgw.codec.modbus.ModbusAddressHelper;
import com.omes.dgw.codec.modbus.ModbusByteOrder;
import com.omes.dgw.codec.modbus.ModbusRegisterArea;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class DevicePointWriteServiceImpl implements DevicePointWriteService {

    private final DeviceMapper deviceMapper;
    private final DevicePointWriteCommandMapper commandMapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<DeviceProtocolGateway> protocolGatewayProvider;
    private final ObjectProvider<DevicePointWriteExecutor> writeExecutorProvider;

    public DevicePointWriteServiceImpl(
            DeviceMapper deviceMapper,
            DevicePointWriteCommandMapper commandMapper,
            ObjectMapper objectMapper,
            ObjectProvider<DeviceProtocolGateway> protocolGatewayProvider,
            ObjectProvider<DevicePointWriteExecutor> writeExecutorProvider
    ) {
        this.deviceMapper = deviceMapper;
        this.commandMapper = commandMapper;
        this.objectMapper = objectMapper;
        this.protocolGatewayProvider = protocolGatewayProvider;
        this.writeExecutorProvider = writeExecutorProvider;
    }

    @Override
    @Transactional
    public DevicePointWriteTaskVo enqueue(DevicePointWriteRequest request) {
        String taskId = requireTaskId(request.getTaskId());
        Device device = requireWritableDevice(request.getDeviceId());
        ResolvedWriteTarget target = resolveWriteTarget(
                device,
                request.getCode(),
                request.getAddress(),
                request.getDataType(),
                request.getScaleFactor(),
                request.getByteOrder());
        validateDataType(device, target.dataType());
        validateByteOrder(device, target.byteOrder());
        validateWritableAddress(device, target.address());
        validateEngineeringRange(target, request.getValue());
        int seqNo = resolveSeqNo(taskId, request.getSeqNo());
        DevicePointWriteCommand command = createPendingCommand(
                taskId, seqNo, device.getId(),
                target.address(), target.dataType(), target.scaleFactor(),
                target.byteOrder(), request.getValue());
        commandMapper.insert(command);
        wakeExecutor(taskId);
        return buildTaskVo(taskId, List.of(command));
    }

    @Override
    @Transactional
    public DevicePointWriteTaskVo enqueueBatch(DevicePointWriteBatchRequest request) {
        String taskId = requireTaskId(request.getTaskId());
        List<DevicePointWriteCommand> commands = new ArrayList<>(request.getItems().size());
        int autoSeq = nextSeqNo(taskId);
        for (DevicePointWriteItemRequest item : request.getItems()) {
            int seqNo = item.getSeqNo() != null ? item.getSeqNo() : autoSeq++;
            Device device = requireWritableDevice(resolveDeviceId(request, item));
            ModbusByteOrder byteOrder = resolveByteOrder(item.getByteOrder(), request.getByteOrder());
            ResolvedWriteTarget target = resolveWriteTarget(
                    device,
                    item.getCode(),
                    item.getAddress(),
                    item.getDataType(),
                    item.getScaleFactor(),
                    byteOrder);
            validateDataType(device, target.dataType());
            validateByteOrder(device, target.byteOrder());
            validateWritableAddress(device, target.address());
            validateEngineeringRange(target, item.getValue());
            DevicePointWriteCommand command = createPendingCommand(
                    taskId, seqNo, device.getId(),
                    target.address(), target.dataType(), target.scaleFactor(),
                    target.byteOrder(), item.getValue());
            commandMapper.insert(command);
            commands.add(command);
        }
        wakeExecutor(taskId);
        return buildTaskVo(taskId, commands);
    }

    @Override
    public PageResult<DevicePointWriteCommandVo> pageHistory(DevicePointWriteCommandQuery query) {
        DevicePointWriteCommandQuery q = query != null ? query : new DevicePointWriteCommandQuery();
        long page = q.getPage() < 1 ? 1 : q.getPage();
        long size = q.getSize() < 1 ? 20 : Math.min(q.getSize(), 200);

        LambdaQueryWrapper<DevicePointWriteCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(q.getTaskId()), DevicePointWriteCommand::getTaskId, q.getTaskId())
                .eq(StringUtils.hasText(q.getDeviceId()), DevicePointWriteCommand::getDeviceId, q.getDeviceId())
                .eq(q.getStatus() != null, DevicePointWriteCommand::getStatus, q.getStatus())
                .like(StringUtils.hasText(q.getAddress()), DevicePointWriteCommand::getAddress, q.getAddress())
                .ge(q.getCreateTimeFrom() != null, DevicePointWriteCommand::getCreateTime, q.getCreateTimeFrom())
                .le(q.getCreateTimeTo() != null, DevicePointWriteCommand::getCreateTime, q.getCreateTimeTo())
                .ge(q.getExecutedTimeFrom() != null, DevicePointWriteCommand::getExecutedTime, q.getExecutedTimeFrom())
                .le(q.getExecutedTimeTo() != null, DevicePointWriteCommand::getExecutedTime, q.getExecutedTimeTo())
                .orderByDesc(DevicePointWriteCommand::getCreateTime)
                .orderByAsc(DevicePointWriteCommand::getSeqNo);

        Page<DevicePointWriteCommand> result = commandMapper.selectPage(new Page<>(page, size), wrapper);
        Page<DevicePointWriteCommandVo> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toCommandVo).toList());
        return PageResult.of(voPage);
    }

    @Override
    public PageResult<DevicePointWriteTaskHistoryVo> pageTaskHistory(DevicePointWriteCommandQuery query) {
        DevicePointWriteCommandQuery q = query != null ? query : new DevicePointWriteCommandQuery();
        long page = q.getPage() < 1 ? 1 : q.getPage();
        long size = q.getSize() < 1 ? 20 : Math.min(q.getSize(), 200);

        if (!StringUtils.hasText(q.getTaskStatus()) && q.getStatus() != null) {
            q.setTaskStatus(q.getStatus().name());
        }
        if (StringUtils.hasText(q.getTaskStatus())) {
            q.setTaskStatus(q.getTaskStatus().trim().toUpperCase());
        }

        Page<DevicePointWriteTaskHistoryVo> pageReq = new Page<>(page, size);
        IPage<DevicePointWriteTaskHistoryVo> result = commandMapper.selectTaskHistoryPage(pageReq, q);
        for (DevicePointWriteTaskHistoryVo record : result.getRecords()) {
            record.setStatus(resolveTaskStatus(record));
            record.setDeviceIds(dedupeCsv(record.getDeviceIds()));
        }
        return PageResult.of(result);
    }

    @Override
    public List<String> listRunnableTaskIds() {
        List<DevicePointWriteCommand> pending = commandMapper.selectList(
                new LambdaQueryWrapper<DevicePointWriteCommand>()
                        .select(DevicePointWriteCommand::getTaskId)
                        .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.PENDING));
        if (pending.isEmpty()) {
            return List.of();
        }
        Set<String> taskIds = new HashSet<>();
        for (DevicePointWriteCommand command : pending) {
            if (StringUtils.hasText(command.getTaskId())) {
                taskIds.add(command.getTaskId());
            }
        }
        if (taskIds.isEmpty()) {
            return List.of();
        }
        List<DevicePointWriteCommand> running = commandMapper.selectList(
                new LambdaQueryWrapper<DevicePointWriteCommand>()
                        .select(DevicePointWriteCommand::getTaskId)
                        .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.RUNNING)
                        .in(DevicePointWriteCommand::getTaskId, taskIds));
        for (DevicePointWriteCommand command : running) {
            taskIds.remove(command.getTaskId());
        }
        return taskIds.stream().sorted().toList();
    }

    @Override
    @Transactional
    public Optional<DevicePointWriteCommand> claimNextPending(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return Optional.empty();
        }
        Long runningCount = commandMapper.selectCount(new LambdaQueryWrapper<DevicePointWriteCommand>()
                .eq(DevicePointWriteCommand::getTaskId, taskId)
                .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.RUNNING));
        if (runningCount != null && runningCount > 0) {
            return Optional.empty();
        }
        DevicePointWriteCommand command = commandMapper.selectOne(
                new LambdaQueryWrapper<DevicePointWriteCommand>()
                        .eq(DevicePointWriteCommand::getTaskId, taskId)
                        .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.PENDING)
                        .orderByAsc(DevicePointWriteCommand::getSeqNo)
                        .orderByAsc(DevicePointWriteCommand::getId)
                        .last("LIMIT 1"));
        if (command == null) {
            return Optional.empty();
        }
        int updated = commandMapper.update(null, new LambdaUpdateWrapper<DevicePointWriteCommand>()
                .set(DevicePointWriteCommand::getStatus, WriteCommandStatus.RUNNING)
                .eq(DevicePointWriteCommand::getId, command.getId())
                .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.PENDING));
        if (updated < 1) {
            return Optional.empty();
        }
        command.setStatus(WriteCommandStatus.RUNNING);
        return Optional.of(command);
    }

    @Override
    public void execute(DevicePointWriteCommand command) {
        LocalDateTime executedTime = LocalDateTime.now();
        try {
            if (!StringUtils.hasText(command.getAddress())) {
                markFailed(command.getId(), "写入地址不能为空", executedTime);
                return;
            }
            if (command.getDataType() == null) {
                markFailed(command.getId(), "数据类型不能为空", executedTime);
                return;
            }
            Device device = requireWritableDevice(command.getDeviceId());
            validateDataType(device, command.getDataType());
            validateWritableAddress(device, command.getAddress());
            Object engineeringValue = parseWriteValue(command.getWriteValue());
            validateEngineeringRange(device, command.getAddress(), engineeringValue);
            DevicePointConfigItem matchedPoint = findPointForWrite(device, command.getAddress());
            Boolean bcdConvert = matchedPoint != null && Boolean.TRUE.equals(matchedPoint.getBcdConvert());
            Integer decimalPlaces = resolveDecimalPlaces(matchedPoint, command.getDataType());
            java.math.BigDecimal scaleFactor = command.getScaleFactor() != null
                    ? command.getScaleFactor()
                    : (matchedPoint != null ? matchedPoint.getScaleFactor() : null);
            Object rawValue = PointValueScaler.toRawWriteValue(
                    engineeringValue, command.getDataType(), scaleFactor, bcdConvert, decimalPlaces);
            if (rawValue == null) {
                markFailed(command.getId(), "写入值无效", executedTime);
                return;
            }
            ProtocolWriteResult result = requireProtocolGateway().write(
                    device.getProtocol().getCode(),
                    device.getId(),
                    new ProtocolWriteRequest(
                            command.getAddress(),
                            rawValue,
                            command.getDataType(),
                            command.getByteOrder()));
            if (result.success()) {
                markSuccess(command.getId(), executedTime);
            } else {
                markFailed(command.getId(), result.failureMessageOrDefault("协议写入失败"), executedTime);
            }
        } catch (BusinessException e) {
            markFailed(command.getId(), e.getMessage(), executedTime);
        } catch (Exception e) {
            markFailed(command.getId(), "执行异常: " + e.getMessage(), executedTime);
        }
    }

    @Override
    @Transactional
    public int recoverStaleRunningCommands() {
        List<DevicePointWriteCommand> stale = commandMapper.selectList(
                new LambdaQueryWrapper<DevicePointWriteCommand>()
                        .select(DevicePointWriteCommand::getTaskId)
                        .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.RUNNING));
        if (stale.isEmpty()) {
            return 0;
        }
        Set<String> taskIds = new HashSet<>();
        for (DevicePointWriteCommand command : stale) {
            if (StringUtils.hasText(command.getTaskId())) {
                taskIds.add(command.getTaskId());
            }
        }
        int updated = commandMapper.update(null, new LambdaUpdateWrapper<DevicePointWriteCommand>()
                .set(DevicePointWriteCommand::getStatus, WriteCommandStatus.PENDING)
                .eq(DevicePointWriteCommand::getStatus, WriteCommandStatus.RUNNING));
        if (updated > 0) {
            log.info("控制点位写入停机重启恢复: 已将 {} 条 RUNNING 指令重置为 PENDING, taskIds={}",
                    updated, taskIds.stream().sorted().toList());
        }
        return updated;
    }

    private static String requireTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException("任务标识不能为空");
        }
        return taskId.trim();
    }

    private String resolveDeviceId(DevicePointWriteBatchRequest request, DevicePointWriteItemRequest item) {
        if (StringUtils.hasText(item.getDeviceId())) {
            return item.getDeviceId();
        }
        if (StringUtils.hasText(request.getDeviceId())) {
            return request.getDeviceId();
        }
        throw new BusinessException("设备ID不能为空");
    }

    private int resolveSeqNo(String taskId, Integer requestedSeq) {
        if (requestedSeq != null) {
            if (requestedSeq < 1) {
                throw new BusinessException("执行序号必须大于0");
            }
            return requestedSeq;
        }
        return nextSeqNo(taskId);
    }

    private int nextSeqNo(String taskId) {
        DevicePointWriteCommand latest = commandMapper.selectOne(
                new LambdaQueryWrapper<DevicePointWriteCommand>()
                        .eq(DevicePointWriteCommand::getTaskId, taskId)
                        .orderByDesc(DevicePointWriteCommand::getSeqNo)
                        .last("LIMIT 1"));
        if (latest == null || latest.getSeqNo() == null) {
            return 1;
        }
        return latest.getSeqNo() + 1;
    }

    private static ModbusByteOrder resolveByteOrder(ModbusByteOrder itemOrder, ModbusByteOrder batchOrder) {
        return itemOrder != null ? itemOrder : batchOrder;
    }

    private ResolvedWriteTarget resolveWriteTarget(
            Device device,
            String code,
            String address,
            PointDataType dataType,
            java.math.BigDecimal scaleFactor,
            ModbusByteOrder byteOrder
    ) {
        if (StringUtils.hasText(code)) {
            DevicePointConfigItem point = DevicePointConfigSupport.findPointByCode(device.getCollectConfig(), code.trim());
            if (point == null) {
                throw new BusinessException("写入点位不存在: " + code.trim());
            }
            PointAccessType accessType = point.getAccessType() != null ? point.getAccessType() : PointAccessType.READ_ONLY;
            if (!accessType.isWritable()) {
                throw new BusinessException("点位不支持写入: " + code.trim());
            }
            if (!StringUtils.hasText(point.getSetpointAddress())) {
                throw new BusinessException("点位未配置设定值点位: " + code.trim());
            }
            if (point.getDataType() == null) {
                throw new BusinessException("写入点位未配置数据类型: " + code.trim());
            }
            if (BitFieldExtractor.isConfigured(point)) {
                throw new BusinessException("位域点位暂不支持写入，请写入完整状态字点位: " + code.trim());
            }
            return new ResolvedWriteTarget(
                    point.getSetpointAddress().trim(),
                    point.getDataType(),
                    scaleFactor != null ? scaleFactor : point.getScaleFactor(),
                    byteOrder != null ? byteOrder : point.getByteOrder(),
                    point.getEngValueMin(),
                    point.getEngValueMax());
        }
        if (!StringUtils.hasText(address)) {
            throw new BusinessException("写入地址不能为空");
        }
        if (dataType == null) {
            throw new BusinessException("数据类型不能为空");
        }
        DevicePointConfigItem byAddress = findPointForWrite(device, address.trim());
        return new ResolvedWriteTarget(
                address.trim(),
                dataType,
                scaleFactor != null ? scaleFactor : (byAddress != null ? byAddress.getScaleFactor() : null),
                byteOrder != null ? byteOrder : (byAddress != null ? byAddress.getByteOrder() : null),
                byAddress != null ? byAddress.getEngValueMin() : null,
                byAddress != null ? byAddress.getEngValueMax() : null);
    }

    private Integer resolveDecimalPlaces(DevicePointConfigItem item, PointDataType dataType) {
        if (item != null && item.getDecimalPlaces() != null) {
            return item.getDecimalPlaces();
        }
        return dataType != null ? dataType.getDefaultDecimalPlaces() : null;
    }

    /**
     * 按写入地址匹配点位：优先设定值地址，其次实际值地址（便于自由写命中点位的 BCD/缩放配置）。
     */
    private DevicePointConfigItem findPointForWrite(Device device, String address) {
        if (device == null || !StringUtils.hasText(address)) {
            return null;
        }
        String normalized = address.trim();
        DeviceCollectConfig config = DevicePointConfigSupport.normalize(device.getCollectConfig());
        DevicePointConfigItem byActual = null;
        for (DevicePointConfigItem item : config.getPoints()) {
            if (item == null) {
                continue;
            }
            if (normalized.equalsIgnoreCase(trimToEmpty(item.getSetpointAddress()))) {
                return item;
            }
            if (byActual == null && normalized.equalsIgnoreCase(trimToEmpty(item.getActualAddress()))) {
                byActual = item;
            }
        }
        return byActual;
    }

    private static String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    private void validateEngineeringRange(ResolvedWriteTarget target, Object value) {
        if (target == null) {
            return;
        }
        validateEngineeringRange(target.engValueMin(), target.engValueMax(), value);
    }

    private void validateEngineeringRange(Device device, String address, Object value) {
        DevicePointConfigItem point = findPointForWrite(device, address);
        if (point == null) {
            return;
        }
        validateEngineeringRange(point.getEngValueMin(), point.getEngValueMax(), value);
    }

    private static void validateEngineeringRange(
            java.math.BigDecimal engValueMin,
            java.math.BigDecimal engValueMax,
            Object value
    ) {
        try {
            PointValueScaler.assertWithinEngRange(value, engValueMin, engValueMax);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private record ResolvedWriteTarget(
            String address,
            PointDataType dataType,
            java.math.BigDecimal scaleFactor,
            ModbusByteOrder byteOrder,
            java.math.BigDecimal engValueMin,
            java.math.BigDecimal engValueMax
    ) {
    }

    private DevicePointWriteCommand createPendingCommand(
            String taskId,
            int seqNo,
            String deviceId,
            String address,
            PointDataType dataType,
            java.math.BigDecimal scaleFactor,
            ModbusByteOrder byteOrder,
            Object value
    ) {
        if (!StringUtils.hasText(address)) {
            throw new BusinessException("写入地址不能为空");
        }
        DevicePointWriteCommand command = new DevicePointWriteCommand();
        command.setTaskId(taskId);
        command.setSeqNo(seqNo);
        command.setDeviceId(deviceId);
        command.setAddress(address.trim());
        command.setDataType(dataType);
        command.setScaleFactor(scaleFactor);
        command.setByteOrder(byteOrder);
        command.setWriteValue(serializeValue(value));
        command.setStatus(WriteCommandStatus.PENDING);
        return command;
    }

    private Device requireWritableDevice(String deviceId) {
        if (!StringUtils.hasText(deviceId)) {
            throw new BusinessException("设备ID不能为空");
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException("设备不存在");
        }
        if (!Boolean.TRUE.equals(device.getEnabled())) {
            throw new BusinessException("设备未启用，无法下发控制指令");
        }
        if (device.getProtocol() == null) {
            throw new BusinessException("设备未配置通信协议");
        }
        return device;
    }

    private void validateDataType(Device device, PointDataType dataType) {
        if (dataType == null) {
            throw new BusinessException("数据类型不能为空");
        }
        if (!dataType.supports(device.getProtocol())) {
            throw new BusinessException("数据类型与设备协议不匹配: " + dataType.getLabel());
        }
    }

    private void validateByteOrder(Device device, ModbusByteOrder byteOrder) {
        if (byteOrder == null) {
            return;
        }
        if (device.getProtocol() != DeviceProtocol.MODBUS_TCP
                && device.getProtocol() != DeviceProtocol.FINS_TCP
                && device.getProtocol() != DeviceProtocol.SLMP_TCP) {
            throw new BusinessException("字节序仅适用于 Modbus TCP / FINS TCP / SLMP TCP 设备");
        }
    }

    private void validateWritableAddress(Device device, String address) {
        if (device.getProtocol() != DeviceProtocol.MODBUS_TCP || !StringUtils.hasText(address)) {
            return;
        }
        ModbusRegisterArea area = ModbusAddressHelper.registerAreaFromAddress(address);
        if (area != null && !area.isWritable()) {
            throw new BusinessException("Modbus 寄存器区不支持写入: " + area.getLabel());
        }
    }

    private String serializeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException("写入值格式无效");
        }
    }

    private Object parseWriteValue(String json) {
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("写入值解析失败");
        }
    }

    private void markSuccess(String commandId, LocalDateTime executedTime) {
        commandMapper.update(null, new LambdaUpdateWrapper<DevicePointWriteCommand>()
                .set(DevicePointWriteCommand::getStatus, WriteCommandStatus.SUCCESS)
                .set(DevicePointWriteCommand::getExecutedTime, executedTime)
                .set(DevicePointWriteCommand::getErrorMessage, null)
                .eq(DevicePointWriteCommand::getId, commandId));
    }

    private void markFailed(String commandId, String message, LocalDateTime executedTime) {
        commandMapper.update(null, new LambdaUpdateWrapper<DevicePointWriteCommand>()
                .set(DevicePointWriteCommand::getStatus, WriteCommandStatus.FAILED)
                .set(DevicePointWriteCommand::getExecutedTime, executedTime)
                .set(DevicePointWriteCommand::getErrorMessage, truncate(message, 1000))
                .eq(DevicePointWriteCommand::getId, commandId));
    }

    private static String truncate(String message, int maxLen) {
        if (message == null || message.length() <= maxLen) {
            return message;
        }
        return message.substring(0, maxLen);
    }

    private void wakeExecutor(String taskId) {
        DevicePointWriteExecutor executor = writeExecutorProvider.getIfAvailable();
        if (executor != null) {
            executor.wake(taskId);
        }
    }

    private DevicePointWriteTaskVo buildTaskVo(String taskId, List<DevicePointWriteCommand> commands) {
        DevicePointWriteTaskVo vo = new DevicePointWriteTaskVo();
        vo.setTaskId(taskId);
        vo.setCommands(commands.stream().map(this::toCommandVo).toList());
        return vo;
    }

    private String resolveTaskStatus(DevicePointWriteTaskHistoryVo task) {
        if (task.getRunningCount() > 0) {
            return WriteCommandStatus.RUNNING.name();
        }
        if (task.getPendingCount() > 0) {
            return WriteCommandStatus.PENDING.name();
        }
        if (task.getFailedCount() > 0 && task.getSuccessCount() > 0) {
            return "PARTIAL";
        }
        if (task.getFailedCount() > 0) {
            return WriteCommandStatus.FAILED.name();
        }
        return WriteCommandStatus.SUCCESS.name();
    }

    private String dedupeCsv(String csv) {
        if (!StringUtils.hasText(csv)) {
            return csv;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            if (StringUtils.hasText(part)) {
                values.add(part.trim());
            }
        }
        return String.join(",", values);
    }

    private DevicePointWriteCommandVo toCommandVo(DevicePointWriteCommand command) {
        DevicePointWriteCommandVo vo = new DevicePointWriteCommandVo();
        vo.setCommandId(command.getId());
        vo.setTaskId(command.getTaskId());
        vo.setSeqNo(command.getSeqNo());
        vo.setDeviceId(command.getDeviceId());
        vo.setAddress(command.getAddress());
        vo.setDataType(command.getDataType());
        vo.setScaleFactor(command.getScaleFactor());
        vo.setByteOrder(command.getByteOrder());
        vo.setValue(parseWriteValue(command.getWriteValue()));
        vo.setStatus(command.getStatus());
        vo.setErrorMessage(command.getErrorMessage());
        vo.setCreateTime(command.getCreateTime());
        vo.setExecutedTime(command.getExecutedTime());
        return vo;
    }

    private DeviceProtocolGateway requireProtocolGateway() {
        DeviceProtocolGateway gateway = protocolGatewayProvider.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException("当前门户未启用协议采集（缺少 omes-dgw-collect）");
        }
        return gateway;
    }
}
