package dev.ceven.testbench.domain.model;

import java.time.OffsetDateTime;

public record IngestionMetric(
        OffsetDateTime messageDatetime,
        OffsetDateTime indexationDatetime,
        Long processingTime,
        Long delayOfProcessing
) {}
