package dev.ceven.testbench.infrastructure.adapters.out.elasticsearch;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;

import java.time.OffsetDateTime;
import java.util.List;

@Document(indexName = "#{@indexNameProvider.getIndexName()}")
public class MessageDocument {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String title;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Long)
    private long timestamp;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private OffsetDateTime messageDatetime;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private OffsetDateTime indexationDatetime;

    @Field(type = FieldType.Long)
    private Long processingTime;

    @Field(type = FieldType.Long)
    private Long delayOfProcessing;

    @Field(type = FieldType.Object)
    private List<IngestionMetricDocument> ingestionHistory;

    private SeqNoPrimaryTerm seqNoPrimaryTerm;

    public MessageDocument() {}

    public MessageDocument(String id, String title, String content, long timestamp, SeqNoPrimaryTerm seqNoPrimaryTerm) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.seqNoPrimaryTerm = seqNoPrimaryTerm;
    }

    public MessageDocument(String id, String title, String content, long timestamp, SeqNoPrimaryTerm seqNoPrimaryTerm,
                           OffsetDateTime messageDatetime, OffsetDateTime indexationDatetime,
                           Long processingTime, Long delayOfProcessing,
                           List<IngestionMetricDocument> ingestionHistory) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.timestamp = timestamp;
        this.seqNoPrimaryTerm = seqNoPrimaryTerm;
        this.messageDatetime = messageDatetime;
        this.indexationDatetime = indexationDatetime;
        this.processingTime = processingTime;
        this.delayOfProcessing = delayOfProcessing;
        this.ingestionHistory = ingestionHistory;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
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

    public List<IngestionMetricDocument> getIngestionHistory() {
        return ingestionHistory;
    }

    public void setIngestionHistory(List<IngestionMetricDocument> ingestionHistory) {
        this.ingestionHistory = ingestionHistory;
    }

    public SeqNoPrimaryTerm getSeqNoPrimaryTerm() {
        return seqNoPrimaryTerm;
    }

    public void setSeqNoPrimaryTerm(SeqNoPrimaryTerm seqNoPrimaryTerm) {
        this.seqNoPrimaryTerm = seqNoPrimaryTerm;
    }
}
