package dev.ceven.testbench.infrastructure.adapters.out.elasticsearch;

import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.OffsetDateTime;

public class IngestionMetricDocument {

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private OffsetDateTime messageDatetime;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private OffsetDateTime indexationDatetime;

    @Field(type = FieldType.Long)
    private Long processingTime;

    @Field(type = FieldType.Long)
    private Long delayOfProcessing;

    public IngestionMetricDocument() {}

    public IngestionMetricDocument(OffsetDateTime messageDatetime, OffsetDateTime indexationDatetime,
                                   Long processingTime, Long delayOfProcessing) {
        this.messageDatetime = messageDatetime;
        this.indexationDatetime = indexationDatetime;
        this.processingTime = processingTime;
        this.delayOfProcessing = delayOfProcessing;
    }

    public OffsetDateTime getMessageDatetime() {
        return messageDatetime;
    }

    public void setMessageDatetime(OffsetDateTime messageDatetime) {
        this.messageDatetime = messageDatetime;
    }

    public OffsetDateTime getIndexationDatetime() {
        return indexationDatetime;
    }

    public void setIndexationDatetime(OffsetDateTime indexationDatetime) {
        this.indexationDatetime = indexationDatetime;
    }

    public Long getProcessingTime() {
        return processingTime;
    }

    public void setProcessingTime(Long processingTime) {
        this.processingTime = processingTime;
    }

    public Long getDelayOfProcessing() {
        return delayOfProcessing;
    }

    public void setDelayOfProcessing(Long delayOfProcessing) {
        this.delayOfProcessing = delayOfProcessing;
    }
}
