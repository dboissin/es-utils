package dev.ceven.testbench.domain.model;

import java.util.List;

public record Message(
        String id,
        String title,
        String content,
        long timestamp,
        Long seqNo,
        Long primaryTerm,
        Long processingStartTime,
        List<IngestionMetric> ingestionHistory
) {
    public Message(String id, String title, String content, long timestamp) {
        this(id, title, content, timestamp, null, null, null, List.of());
    }

    public Message(String id, String title, String content, long timestamp, Long seqNo, Long primaryTerm, Long processingStartTime) {
        this(id, title, content, timestamp, seqNo, primaryTerm, processingStartTime, List.of());
    }

    public Message withId(String newId) {
        return new Message(newId, title, content, timestamp, seqNo, primaryTerm, processingStartTime, ingestionHistory);
    }

    public Message withTitle(String newTitle) {
        return new Message(id, newTitle, content, timestamp, seqNo, primaryTerm, processingStartTime, ingestionHistory);
    }

    public Message withContent(String newContent) {
        return new Message(id, title, newContent, timestamp, seqNo, primaryTerm, processingStartTime, ingestionHistory);
    }

    public Message withTimestamp(long newTimestamp) {
        return new Message(id, title, content, newTimestamp, seqNo, primaryTerm, processingStartTime, ingestionHistory);
    }
}
