package dev.ceven.testbench.infrastructure.adapters.out.elasticsearch;

import dev.ceven.testbench.domain.model.Message;
import dev.ceven.testbench.domain.model.IngestionMetric;
import dev.ceven.testbench.application.ports.out.MessageRepositoryPort;
import dev.ceven.testbench.config.IndexNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SeqNoPrimaryTerm;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Component
public class ElasticsearchMessageAdapter implements MessageRepositoryPort {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchMessageAdapter.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final SpringDataElasticsearchMessageRepository repository;
    private final IndexNameProvider indexNameProvider;

    @Value("${app.consumer.approach:operations}")
    private String approach;

    public ElasticsearchMessageAdapter(
            ElasticsearchOperations elasticsearchOperations,
            SpringDataElasticsearchMessageRepository repository,
            IndexNameProvider indexNameProvider) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.repository = repository;
        this.indexNameProvider = indexNameProvider;
    }

    @Override
    public List<Message> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Iterable<MessageDocument> documents = repository.findAllById(ids);
        List<Message> list = new ArrayList<>();
        for (MessageDocument doc : documents) {
            if (doc != null) {
                Long seqNo = doc.getSeqNoPrimaryTerm() != null ? doc.getSeqNoPrimaryTerm().sequenceNumber() : null;
                Long primaryTerm = doc.getSeqNoPrimaryTerm() != null ? doc.getSeqNoPrimaryTerm().primaryTerm() : null;
                
                List<IngestionMetric> history = new ArrayList<>();
                if (doc.getIngestionHistory() != null) {
                    for (IngestionMetricDocument metricDoc : doc.getIngestionHistory()) {
                        history.add(new IngestionMetric(
                                metricDoc.getMessageDatetime(),
                                metricDoc.getIndexationDatetime(),
                                metricDoc.getProcessingTime(),
                                metricDoc.getDelayOfProcessing()
                        ));
                    }
                }

                list.add(new Message(
                        doc.getId(),
                        doc.getTitle(),
                        doc.getContent(),
                        doc.getTimestamp(),
                        seqNo,
                        primaryTerm,
                        null,
                        history
                ));
            }
        }
        return list;
    }

    @Override
    public void saveAll(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        long indexationTime = System.currentTimeMillis();
        List<MessageDocument> documents = messages.stream()
                .map(m -> {
                    SeqNoPrimaryTerm term = (m.seqNo() != null && m.primaryTerm() != null)
                            ? new SeqNoPrimaryTerm(m.seqNo(), m.primaryTerm())
                            : null;
                    
                    List<IngestionMetricDocument> historyDocs = new ArrayList<>();
                    if (m.ingestionHistory() != null) {
                        for (IngestionMetric metric : m.ingestionHistory()) {
                            historyDocs.add(new IngestionMetricDocument(
                                    metric.messageDatetime(),
                                    metric.indexationDatetime(),
                                    metric.processingTime(),
                                    metric.delayOfProcessing()
                            ));
                        }
                    }

                    OffsetDateTime messageDatetime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(m.timestamp()), ZoneId.systemDefault());
                    OffsetDateTime indexationDatetime = OffsetDateTime.ofInstant(Instant.ofEpochMilli(indexationTime), ZoneId.systemDefault());
                    long processingTime = indexationTime - (m.processingStartTime() != null ? m.processingStartTime() : indexationTime);
                    long delayOfProcessing = indexationTime - m.timestamp();

                    historyDocs.add(new IngestionMetricDocument(
                            messageDatetime,
                            indexationDatetime,
                            processingTime,
                            delayOfProcessing
                    ));

                    return new MessageDocument(
                            m.id(),
                            m.title(),
                            m.content(),
                            m.timestamp(),
                            term,
                            messageDatetime,
                            indexationDatetime,
                            processingTime,
                            delayOfProcessing,
                            historyDocs
                    );
                })
                .toList();

        String activeIndex = indexNameProvider.getIndexName();

        if ("repository".equalsIgnoreCase(approach)) {
            logger.info("Outbound ES Adapter: Saving {} documents using Spring Data Repository (Approach B)", documents.size());
            repository.saveAll(documents);
        } else {
            logger.info("Outbound ES Adapter: Saving {} documents using ElasticsearchOperations.bulkIndex (Approach A) to '{}'", documents.size(), activeIndex);
            List<IndexQuery> queries = new ArrayList<>();
            for (MessageDocument doc : documents) {
                IndexQueryBuilder builder = new IndexQueryBuilder()
                        .withId(doc.getId())
                        .withObject(doc);
                if (doc.getSeqNoPrimaryTerm() != null) {
                    builder.withSeqNoPrimaryTerm(doc.getSeqNoPrimaryTerm());
                }
                queries.add(builder.build());
            }
            elasticsearchOperations.bulkIndex(queries, IndexCoordinates.of(activeIndex));
        }
    }
}
