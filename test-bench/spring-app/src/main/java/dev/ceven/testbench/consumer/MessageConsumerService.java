package dev.ceven.testbench.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import dev.ceven.testbench.config.IndexNameProvider;
import dev.ceven.testbench.model.Message;
import dev.ceven.testbench.repository.MessageRepository;

import java.util.ArrayList;
import java.util.List;

@Service
public class MessageConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumerService.class);

    private final ElasticsearchOperations elasticsearchOperations;
    private final MessageRepository messageRepository;
    private final IndexNameProvider indexNameProvider;

    @Value("${app.consumer.enabled:true}")
    private boolean enabled;

    @Value("${app.consumer.approach:operations}")
    private String approach;

    private final java.util.concurrent.atomic.AtomicInteger totalIndexed = new java.util.concurrent.atomic.AtomicInteger(
            0);

    @Value("${app.producer.message-count:10000}")
    private int expectedCount;

    public MessageConsumerService(
            ElasticsearchOperations elasticsearchOperations,
            MessageRepository messageRepository,
            IndexNameProvider indexNameProvider) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.messageRepository = messageRepository;
        this.indexNameProvider = indexNameProvider;
    }

    @KafkaListener(topics = "${app.topic-name}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<ConsumerRecord<String, Message>> records, Acknowledgment ack) {
        if (!enabled) {
            logger.debug("Consumer is disabled, skipping batch processing.");
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        String activeIndex = indexNameProvider.getIndexName();
        logger.info("Received batch of {} records from Kafka. Processing...", records.size());

        try {
            // Extract payloads
            List<Message> messages = records.stream()
                    .map(ConsumerRecord::value)
                    .toList();

            // PERFORMANCE & CONSISTENCY CONSIDERATIONS:
            // 1. Idempotency: We ensure that the Elasticsearch document id matches our
            // message ID.
            // If a message is re-delivered, Elasticsearch updates the existing document
            // instead of duplicating.
            // 2. Bulk Indexing: We index all records in the batch together rather than
            // making individual HTTP calls.

            if ("repository".equalsIgnoreCase(approach)) {
                runRepositoryApproach(messages);
            } else {
                runOperationsApproach(messages, activeIndex);
            }

            // CONSISTENCY: Commit Kafka offset ONLY after successful indexing in
            // Elasticsearch.
            // If the bulk index fails, this line is skipped, throwing an exception, and
            // Kafka will retry the batch.
            ack.acknowledge();

            int currentTotal = totalIndexed.addAndGet(records.size());
            long duration = System.currentTimeMillis() - startTime;
            logger.info(
                    "Successfully indexed batch of {} messages to index '{}' in {} ms. Kafka offset committed. (Total: {}/{})",
                    records.size(), activeIndex, duration, currentTotal, expectedCount);

            if (currentTotal >= expectedCount) {
                logger.info("Processed expected count of {} messages. Initiating self-shutdown...", expectedCount);
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {
                    }
                    logger.info("Exiting application now.");
                    System.exit(0);
                }).start();
            }

        } catch (Exception e) {
            logger.error("Failed to index message batch to Elasticsearch. Offset NOT committed. Message: {}",
                    e.getMessage(), e);
            // Throw exception to trigger Kafka error handling / retry mechanisms
            throw new RuntimeException("Elasticsearch indexing failed, rolling back consumer offset", e);
        }
    }

    /**
     * APPROACH A: Using ElasticsearchOperations.bulkIndex(...)
     * Highly flexible, allows explicit control over the mapping, index name,
     * routing, and queries list.
     */
    private void runOperationsApproach(List<Message> messages, String indexName) {
        logger.info("Using Approach A (ElasticsearchOperations.bulkIndex) to write to '{}'", indexName);

        List<IndexQuery> queries = new ArrayList<>();
        for (Message message : messages) {
            IndexQuery query = new IndexQueryBuilder()
                    .withId(message.id()) // Idempotency: use business message ID
                    .withObject(message)
                    .build();
            queries.add(query);
        }

        // Execute Bulk Indexing
        elasticsearchOperations.bulkIndex(queries, IndexCoordinates.of(indexName));
    }

    /**
     * APPROACH B: Using MessageRepository.saveAll(...)
     * High-level repository abstraction. Spring Data Elasticsearch automatically
     * batches
     * entities and executes a bulk index under the hood.
     */
    private void runRepositoryApproach(List<Message> messages) {
        logger.info("Using Approach B (MessageRepository.saveAll) to write to dynamic index");

        // saveAll internally aggregates the entities and performs a Bulk index.
        messageRepository.saveAll(messages);
    }
}
