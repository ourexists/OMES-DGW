package com.omes.dgw.device.online;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.omes.dgw.device.entity.DeviceOnlineRecord;
import com.omes.dgw.device.mapper.DeviceOnlineRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.collect.online-event", name = "enabled", havingValue = "true")
public class DeviceOnlineEventBatchWriter
        extends ServiceImpl<DeviceOnlineRecordMapper, DeviceOnlineRecord> {

    public void applyBatch(List<DeviceOnlineEventQueueItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<DeviceOnlineRecord> inserts = new ArrayList<>();
        int updateCount = 0;
        for (DeviceOnlineEventQueueItem item : items) {
            if (item.getOp() == DeviceOnlineEventOp.INSERT) {
                DeviceOnlineRecord row = toInsertRow(item);
                if (row != null) {
                    inserts.add(row);
                }
            } else if (item.getOp() == DeviceOnlineEventOp.UPDATE) {
                if (applyCloseUpdate(item)) {
                    updateCount++;
                }
            }
        }
        if (!inserts.isEmpty()) {
            saveBatch(inserts);
            log.debug("在线态时段批量插入 {} 条", inserts.size());
        }
        if (updateCount > 0) {
            log.debug("在线态时段批量闭合 {} 条", updateCount);
        }
    }

    private DeviceOnlineRecord toInsertRow(DeviceOnlineEventQueueItem item) {
        if (!StringUtils.hasText(item.getEventId()) || !StringUtils.hasText(item.getSn())
                || item.getState() == null || item.getStartTime() == null) {
            log.warn("跳过在线态插入: 必填字段缺失 sn={}, eventId={}", item.getSn(), item.getEventId());
            return null;
        }
        DeviceOnlineRecord row = new DeviceOnlineRecord();
        String id = StringUtils.hasText(item.getId()) ? item.getId() : item.getEventId();
        row.setId(id);
        row.setEventId(item.getEventId());
        row.setSn(item.getSn());
        row.setState(item.getState());
        row.setStartTime(item.getStartTime());
        row.setEndTime(item.getEndTime());
        return row;
    }

    private boolean applyCloseUpdate(DeviceOnlineEventQueueItem item) {
        if (!StringUtils.hasText(item.getEventId()) || item.getEndTime() == null) {
            log.warn("跳过在线态闭合: eventId 或 endTime 为空, sn={}", item.getSn());
            return false;
        }
        return lambdaUpdate()
                .eq(DeviceOnlineRecord::getEventId, item.getEventId())
                .isNull(DeviceOnlineRecord::getEndTime)
                .set(DeviceOnlineRecord::getEndTime, item.getEndTime())
                .update();
    }
}
