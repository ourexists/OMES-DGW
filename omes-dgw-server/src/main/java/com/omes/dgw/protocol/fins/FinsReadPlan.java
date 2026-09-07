package com.omes.dgw.protocol.fins;

import com.omes.dgw.device.entity.DevicePoint;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FINS 轮询读计划：按内存区分组、合并连续字地址；同字内多位合并为一次字读。
 */
public final class FinsReadPlan {

    private final List<FinsPollingChunk> wordChunks;
    private final List<FinsPollingChunk> bitWordChunks;
    private final List<FinsPollingTag> fallbackTags;

    public FinsReadPlan(
            List<FinsPollingChunk> wordChunks,
            List<FinsPollingChunk> bitWordChunks,
            List<FinsPollingTag> fallbackTags
    ) {
        this.wordChunks = List.copyOf(wordChunks);
        this.bitWordChunks = List.copyOf(bitWordChunks);
        this.fallbackTags = List.copyOf(fallbackTags);
    }

    public List<FinsPollingChunk> wordChunks() {
        return wordChunks;
    }

    public List<FinsPollingChunk> bitWordChunks() {
        return bitWordChunks;
    }

    public List<FinsPollingTag> fallbackTags() {
        return fallbackTags;
    }

    public static FinsReadPlan build(
            Map<String, String> tagAddresses,
            Map<String, DevicePoint> pointsByName,
            int maxMergeGapWords,
            int maxWordsPerRead
    ) {
        Map<String, List<FinsPollingTag>> wordGroups = new LinkedHashMap<>();
        Map<String, List<FinsPollingTag>> bitWordGroups = new LinkedHashMap<>();
        List<FinsPollingTag> fallback = new ArrayList<>();
        int wordCap = FinsLimits.effectiveMaxWords(maxWordsPerRead, FinsLimits.MAX_WORDS_ABSOLUTE);

        if (tagAddresses != null) {
            for (Map.Entry<String, String> entry : tagAddresses.entrySet()) {
                String tagName = entry.getKey();
                String address = entry.getValue();
                if (!StringUtils.hasText(address)) {
                    continue;
                }
                DevicePoint point = pointsByName != null ? pointsByName.get(tagName) : null;
                try {
                    FinsPollingTag tag = FinsAddressHelper.toPollingTag(tagName, address, point);
                    if (tag.bitAccess()) {
                        String key = tag.wordAreaCodeForRead() + ":" + tag.wordAddress();
                        bitWordGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(tag);
                    } else {
                        String key = Integer.toHexString(tag.memoryAreaCode());
                        wordGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(tag);
                    }
                } catch (IllegalArgumentException e) {
                    fallback.add(new FinsPollingTag(tagName, 0, 0, null, false, 1, point));
                }
            }
        }

        List<FinsPollingChunk> wordChunks = new ArrayList<>();
        for (List<FinsPollingTag> tags : wordGroups.values()) {
            wordChunks.addAll(buildWordChunks(tags, maxMergeGapWords, wordCap));
        }
        List<FinsPollingChunk> bitWordChunks = new ArrayList<>();
        for (List<FinsPollingTag> tags : bitWordGroups.values()) {
            if (tags.isEmpty()) {
                continue;
            }
            FinsPollingTag first = tags.getFirst();
            bitWordChunks.add(new FinsPollingChunk(
                    first.wordAreaCodeForRead(),
                    first.wordAddress(),
                    1,
                    List.copyOf(tags)));
        }
        return new FinsReadPlan(wordChunks, bitWordChunks, fallback);
    }

    private static List<FinsPollingChunk> buildWordChunks(
            List<FinsPollingTag> tags, int maxMergeGapWords, int maxWordsPerRead
    ) {
        List<FinsPollingChunk> chunks = new ArrayList<>();
        if (tags.isEmpty()) {
            return chunks;
        }
        tags.sort(Comparator.comparingInt(FinsPollingTag::wordAddress));
        int i = 0;
        while (i < tags.size()) {
            FinsPollingTag first = tags.get(i);
            int start = first.wordAddress();
            int end = first.endWordAddress();
            List<FinsPollingTag> chunkTags = new ArrayList<>();
            chunkTags.add(first);
            i++;
            while (i < tags.size()) {
                FinsPollingTag next = tags.get(i);
                int gap = next.wordAddress() - end - 1;
                int newEnd = Math.max(end, next.endWordAddress());
                if (gap > Math.max(0, maxMergeGapWords) || newEnd - start + 1 > maxWordsPerRead) {
                    break;
                }
                chunkTags.add(next);
                end = newEnd;
                i++;
            }
            chunks.add(new FinsPollingChunk(
                    first.memoryAreaCode(),
                    start,
                    end - start + 1,
                    List.copyOf(chunkTags)));
        }
        return chunks;
    }
}
