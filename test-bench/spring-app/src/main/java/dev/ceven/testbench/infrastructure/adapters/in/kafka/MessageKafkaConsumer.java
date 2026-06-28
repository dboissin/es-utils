package dev.ceven.testbench.infrastructure.adapters.in.kafka;

import dev.ceven.testbench.application.ports.in.ProcessMessageUseCase;
import dev.ceven.testbench.domain.model.Message;
import dev.ceven.testbench.config.IndexNameProvider;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MessageKafkaConsumer {
    private static final Logger logger = LoggerFactory.getLogger(MessageKafkaConsumer.class);

    private final ProcessMessageUseCase processMessageUseCase;
    private final IndexNameProvider indexNameProvider;

    @Value("${app.consumer.enabled:true}")
    private boolean enabled;

    @Value("${app.producer.message-count:10000}")
    private int expectedCount;

    private final AtomicInteger totalIndexed = new AtomicInteger(0);

    public MessageKafkaConsumer(
            ProcessMessageUseCase processMessageUseCase,
            IndexNameProvider indexNameProvider) {
        this.processMessageUseCase = processMessageUseCase;
        this.indexNameProvider = indexNameProvider;
    }

    @KafkaListener(topics = "${app.topic-name}", groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(List<ConsumerRecord<String, dev.ceven.testbench.model.Message>> records, Acknowledgment ack) {
        if (!enabled) {
            logger.debug("Consumer is disabled, skipping batch processing.");
            return;
        }

        if (records == null || records.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        String activeIndex = indexNameProvider.getIndexName();
        logger.info("Received batch of {} records from Kafka. Processing via Hexagonal Core...", records.size());

        try {
            List<Message> domainMessages = records.stream()
                    .map(ConsumerRecord::value)
                    .map(dto -> new Message(
                            dto.id(),
                            dto.title(),
                            dto.content(),
                            dto.timestamp(),
                            null,
                            null,
                            startTime
                    ))
                    .toList();

            // Execute the inbound port
            processMessageUseCase.processAndIndex(domainMessages);

            // Acknowledge offsets
            ack.acknowledge();

            int currentTotal = totalIndexed.addAndGet(records.size());
            long duration = System.currentTimeMillis() - startTime;
            logger.info(
                    "Successfully processed and indexed batch of {} messages to index '{}' in {} ms. Kafka offset committed. (Total: {}/{})",
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
            logger.error("Failed to process message batch. Offset NOT committed. Message: {}", e.getMessage(), e);
            throw new RuntimeException("Hexagonal processing failed, rolling back consumer offset", e);
        }
    }
}
