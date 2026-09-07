package com.omes.dgw.protocol.slmp;

import com.omes.dgw.device.entity.DevicePoint;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SLMP 轮询读计划：按设备码与字/位访问分组，合并连续地址并遵守 MC 协议单次点数/报文上限。
 */
public final class SlmpReadPlan {

    private final List<SlmpPollingChunk> wordChunks;
    private final List<SlmpPollingChunk> bitChunks;
    private final List<SlmpPollingTag> fallbackTags;

    public SlmpReadPlan(
            List<SlmpPollingChunk> wordChunks,
            List<SlmpPollingChunk> bitChunks,
            List<SlmpPollingTag> fallbackTags
    ) {
        this.wordChunks = List.copyOf(wordChunks);
        this.bitChunks = List.copyOf(bitChunks);
        this.fallbackTags = List.copyOf(fallbackTags);
    }

    public List<SlmpPollingChunk> wordChunks() {
        return wordChunks;
    }

    public List<SlmpPollingChunk> bitChunks() {
        return bitChunks;
    }

    public List<SlmpPollingTag> fallbackTags() {
        return fallbackTags;
    }

    public static SlmpReadPlan build(
            Map<String, String> tagAddresses,
            Map<String, DevicePoint> pointsByName,
            int maxMergeGapWords,
            int maxMergeGapBits,
            int maxWordsPerRequest,
            int maxBitsPerRequest
    ) {
        Map<String, List<SlmpPollingTag>> wordGroups = new LinkedHashMap<>();
        Map<String, List<SlmpPollingTag>> bitGroups = new LinkedHashMap<>();
        List<SlmpPollingTag> fallback = new ArrayList<>();
        int wordCap = SlmpLimits.effectiveMaxWords(maxWordsPerRequest);
        int bitCap = SlmpLimits.effectiveMaxBits(maxBitsPerRequest);

        if (tagAddresses != null) {
            for (Map.Entry<String, String> entry : tagAddresses.entrySet()) {
                String tagName = entry.getKey();
                String address = entry.getValue();
                if (!StringUtils.hasText(address)) {
                    continue;
                }
                DevicePoint point = pointsByName != null ? pointsByName.get(tagName) : null;
                try {
                    SlmpPollingTag tag = SlmpAddressHelper.toPollingTag(tagName, address, point);
                    String groupKey = Integer.toHexString(tag.deviceCode());
                    if (tag.bitAccess()) {
                        bitGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag);
                    } else {
                        wordGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(tag);
                    }
                } catch (IllegalArgumentException e) {
                    fallback.add(new SlmpPollingTag(tagName, 0, 0, false, 1, point));
                }
            }
        }

        List<SlmpPollingChunk> wordChunks = new ArrayList<>();
        for (List<SlmpPollingTag> tags : wordGroups.values()) {
            wordChunks.addAll(buildWordChunks(tags, maxMergeGapWords, wordCap));
        }
        List<SlmpPollingChunk> bitChunks = new ArrayList<>();
        for (List<SlmpPollingTag> tags : bitGroups.values()) {
            bitChunks.addAll(buildBitChunks(tags, maxMergeGapBits, bitCap));
        }
        return new SlmpReadPlan(wordChunks, bitChunks, fallback);
    }

    private static List<SlmpPollingChunk> buildWordChunks(
            List<SlmpPollingTag> tags, int maxMergeGapWords, int maxWordsPerRequest
    ) {
        List<SlmpPollingChunk> chunks = new ArrayList<>();
        if (tags.isEmpty()) {
            return chunks;
        }
        tags.sort(Comparator.comparingInt(SlmpPollingTag::deviceNumber));
        int i = 0;
        while (i < tags.size()) {
            SlmpPollingTag first = tags.get(i);
            int start = first.deviceNumber();
            int end = first.endDeviceNumber();
            List<SlmpPollingTag> chunkTags = new ArrayList<>();
            chunkTags.add(first);
            i++;
            while (i < tags.size()) {
                SlmpPollingTag next = tags.get(i);
                int gap = next.deviceNumber() - end - 1;
                int newEnd = Math.max(end, next.endDeviceNumber());
                if (gap > Math.max(0, maxMergeGapWords) || newEnd - start + 1 > maxWordsPerRequest) {
                    break;
                }
                chunkTags.add(next);
                end = newEnd;
                i++;
            }
            chunks.add(new SlmpPollingChunk(
                    first.deviceCode(),
                    false,
                    start,
                    end - start + 1,
                    List.copyOf(chunkTags)));
        }
        return chunks;
    }

    private static List<SlmpPollingChunk> buildBitChunks(
            List<SlmpPollingTag> tags, int maxMergeGapBits, int maxBitsPerRequest
    ) {
        List<SlmpPollingChunk> chunks = new ArrayList<>();
        if (tags.isEmpty()) {
            return chunks;
        }
        tags.sort(Comparator.comparingInt(SlmpPollingTag::deviceNumber));
        int i = 0;
        while (i < tags.size()) {
            SlmpPollingTag first = tags.get(i);
            int start = first.deviceNumber();
            int end = first.deviceNumber();
            List<SlmpPollingTag> chunkTags = new ArrayList<>();
            chunkTags.add(first);
            i++;
            while (i < tags.size()) {
                SlmpPollingTag next = tags.get(i);
                int gap = next.deviceNumber() - end - 1;
                int newEnd = next.deviceNumber();
                if (gap > Math.max(0, maxMergeGapBits) || newEnd - start + 1 > maxBitsPerRequest) {
                    break;
                }
                chunkTags.add(next);
                end = newEnd;
                i++;
            }
            chunks.add(new SlmpPollingChunk(
                    first.deviceCode(),
                    true,
                    start,
                    end - start + 1,
                    List.copyOf(chunkTags)));
        }
        return chunks;
    }
}
