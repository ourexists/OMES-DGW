package com.omes.dgw.device.online;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.omes.dgw.device.cache.DeviceRealtimeObj;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 状态变化时：闭合上一时段（UPDATE event_id 对应记录的 end_time），并开启新时段（INSERT）。
 * 对齐 {@code t_equip_record_online}：每条记录一个 state + start_time + end_time。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.online-event", name = "enabled", havingValue = "true")
public class DeviceOnlineEventCollector {

    private final DeviceOnlineEventQueueService queueService;
    private final ObjectProvider<DeviceOnlineEventConsumer> consumer;

    public DeviceOnlineEventCollector(
            DeviceOnlineEventQueueService queueService,
            ObjectProvider<DeviceOnlineEventConsumer> consumer
    ) {
        this.queueService = queueService;
        this.consumer = consumer;
    }

    public void beforePersist(DeviceRealtimeObj previous, DeviceRealtimeObj current) {
        if (current == null || !StringUtils.hasText(current.getDeviceId())) {
            return;
        }
        if (!StringUtils.hasText(resolveSn(current))) {
            log.warn("设备编码为空，跳过在线态采集 deviceId={}", current.getDeviceId());
            return;
        }

        boolean prevOnline = previous != null && previous.isOnline();
        boolean currOnline = current.isOnline();
        LocalDateTime now = eventTime(current);

        if (prevOnline == currOnline) {
            if (previous != null && StringUtils.hasText(previous.getEventId())) {
                current.setEventId(previous.getEventId());
            }
            return;
        }

        if (previous != null && StringUtils.hasText(previous.getEventId())) {
            enqueueClose(previous.getEventId(), previous, now);
        }

        String newEventId = IdWorker.getIdStr();
        current.setEventId(newEventId);
        enqueueOpen(newEventId, current, DeviceOnlineState.fromOnline(currOnline), now);

        log.info("设备状态变更 sn={}, {} -> {}, closeEventId={}, openEventId={}",
                resolveSn(current),
                prevOnline ? "在线" : "离线",
                currOnline ? "在线" : "离线",
                previous != null ? previous.getEventId() : null,
                newEventId);

        consumer.ifAvailable(DeviceOnlineEventConsumer::onEnqueued);
    }

    private void enqueueClose(String eventId, DeviceRealtimeObj snapshot, LocalDateTime endTime) {
        DeviceOnlineEventQueueItem item = new DeviceOnlineEventQueueItem();
        item.setOp(DeviceOnlineEventOp.UPDATE);
        item.setEventId(eventId);
        item.setSn(resolveSn(snapshot));
        item.setDeviceId(snapshot.getDeviceId());
        item.setEndTime(endTime);
        queueService.enqueue(item);
    }

    private void enqueueOpen(String eventId, DeviceRealtimeObj current, DeviceOnlineState state, LocalDateTime startTime) {
        DeviceOnlineEventQueueItem item = new DeviceOnlineEventQueueItem();
        item.setOp(DeviceOnlineEventOp.INSERT);
        item.setId(eventId);
        item.setEventId(eventId);
        item.setSn(resolveSn(current));
        item.setDeviceId(current.getDeviceId());
        item.setState(state.getCode());
        item.setStartTime(startTime);
        item.setEndTime(null);
        queueService.enqueue(item);
    }

    private static String resolveSn(DeviceRealtimeObj vo) {
        if (StringUtils.hasText(vo.getDeviceCode())) {
            return vo.getDeviceCode();
        }
        return vo.getDeviceId();
    }

    private static LocalDateTime eventTime(DeviceRealtimeObj current) {
        return current.getLastUpdateTime() != null ? current.getLastUpdateTime() : LocalDateTime.now();
    }
}
