package com.omes.dgw.protocol.modbus;

import com.omes.dgw.codec.modbus.ModbusNumericCodec;

import com.omes.dgw.codec.modbus.ModbusAddressHelper;

import com.omes.dgw.device.entity.DevicePoint;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modbus 轮询读计划：启动时预分组、排序、切块，避免每轮 cron 重复计算。
 */
public final class ModbusReadPlan {

    private final List<ModbusPollingChunk> chunks;
    private final List<ModbusPollingFallbackTag> fallbackTags;

    public ModbusReadPlan(List<ModbusPollingChunk> chunks, List<ModbusPollingFallbackTag> fallbackTags) {
        this.chunks = List.copyOf(chunks);
        this.fallbackTags = List.copyOf(fallbackTags);
    }

    public List<ModbusPollingChunk> chunks() {
        return chunks;
    }

    public List<ModbusPollingFallbackTag> fallbackTags() {
        return fallbackTags;
    }

    public static ModbusReadPlan build(
            Map<String, String> tagAddresses,
            Map<String, DevicePoint> pointsByName,
            int maxMergeGapRegisters,
            int maxMergeGapBits
    ) {
        Map<String, List<ModbusPollingSimpleTag>> grouped = new LinkedHashMap<>();
        List<ModbusPollingFallbackTag> fallback = new ArrayList<>();
        if (tagAddresses != null) {
            for (Map.Entry<String, String> e : tagAddresses.entrySet()) {
                DevicePoint point = pointsByName != null ? pointsByName.get(e.getKey()) : null;
                Optional<ModbusAddressHelper.SimplePlcAddress> parsedOpt =
                        ModbusAddressHelper.parseSimplePlcAddress(e.getValue());
                if (parsedOpt.isPresent()) {
                    ModbusAddressHelper.SimplePlcAddress parsed = parsedOpt.get();
                    grouped.computeIfAbsent(parsed.areaName(), k -> new ArrayList<>())
                            .add(new ModbusPollingSimpleTag(
                                    e.getKey(), parsed.areaName(), parsed.pduOffset(), point));
                } else if (StringUtils.hasText(e.getValue())) {
                    fallback.add(new ModbusPollingFallbackTag(e.getKey(), e.getValue(), point));
                }
            }
        }
        List<ModbusPollingChunk> chunks = new ArrayList<>();
        for (Map.Entry<String, List<ModbusPollingSimpleTag>> group : grouped.entrySet()) {
            String area = group.getKey();
            List<ModbusPollingSimpleTag> tags = group.getValue();
            tags.sort(Comparator.comparingInt(ModbusPollingSimpleTag::pduOffset));
            int maxSpan = maxSpanForArea(area);
            int maxGap = maxMergeGapForArea(area, maxMergeGapRegisters, maxMergeGapBits);
            int i = 0;
            while (i < tags.size()) {
                ModbusPollingSimpleTag first = tags.get(i);
                int startPdu = first.pduOffset();
                int endPdu = startPdu + registerSpan(first) - 1;
                List<ModbusPollingSimpleTag> chunkTags = new ArrayList<>();
                chunkTags.add(first);
                i++;
                while (i < tags.size()) {
                    ModbusPollingSimpleTag next = tags.get(i);
                    int gap = next.pduOffset() - endPdu - 1;
                    if (gap > maxGap) {
                        break;
                    }
                    int newEndPdu = Math.max(endPdu, next.pduOffset() + registerSpan(next) - 1);
                    if (newEndPdu - startPdu + 1 <= maxSpan) {
                        chunkTags.add(next);
                        endPdu = newEndPdu;
                        i++;
                    } else {
                        break;
                    }
                }
                chunks.add(new ModbusPollingChunk(area, startPdu, endPdu, List.copyOf(chunkTags)));
            }
        }
        return new ModbusReadPlan(chunks, fallback);
    }

    private static int registerSpan(ModbusPollingSimpleTag tag) {
        if (tag.point() == null) {
            return 1;
        }
        return ModbusNumericCodec.registerCount(tag.point());
    }

    private static int maxMergeGapForArea(String area, int maxMergeGapRegisters, int maxMergeGapBits) {
        if ("coil".equals(area) || "discrete-input".equals(area)) {
            return Math.max(0, maxMergeGapBits);
        }
        return Math.max(0, maxMergeGapRegisters);
    }

    private static int maxSpanForArea(String area) {
        if ("coil".equals(area) || "discrete-input".equals(area)) {
            return 2000;
        }
        return 125;
    }
}
