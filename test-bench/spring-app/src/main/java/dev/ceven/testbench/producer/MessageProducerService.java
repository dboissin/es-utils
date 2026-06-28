package dev.ceven.testbench.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import dev.ceven.testbench.model.Message;

@Service
public class MessageProducerService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MessageProducerService.class);

    private final KafkaTemplate<String, Message> kafkaTemplate;

    @Value("${app.topic-name}")
    private String topicName;

    @Value("${app.producer.enabled:true}")
    private boolean enabled;

    @Value("${app.producer.message-count:10000}")
    private int messageCount;

    @Value("${app.producer.delta-mode:false}")
    private boolean deltaMode;

    public MessageProducerService(KafkaTemplate<String, Message> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            logger.info("Message Producer is disabled via configuration.");
            return;
        }

        logger.info("Starting production of {} messages to topic '{}' (deltaMode={})...",
                messageCount, topicName, deltaMode);

        long baseTime = System.currentTimeMillis() - (messageCount * 1000L);
        int sentCount = 0;
        for (int i = 0; i < messageCount; i++) {
            String id = String.format("msg-%06d", i);

            // Consistency: Route messages with the same ID as the Kafka key
            // This guarantees that all updates/versions of a specific message
            // go to the same Kafka partition and are processed in strict order.
            String partitionKey = id;
            long msgTimestamp = baseTime + (1000L * i);

            if (deltaMode) {
                // 1. Simulate DELETION in Index B (Skip producing some keys that exist in A)
                if (i % 200 == 0) {
                    logger.debug("DeltaMode: skipping production of {} to simulate deletion", id);
                    continue;
                }

                // 2. Simulate MODIFICATION in Index B (Update payload for some existing keys)
                if (i % 200 == 50) {
                    Message modifiedMsg = new Message(
                            id,
                            "Modified Title " + i,
                            "Modified content for message " + i + " at current time.",
                            msgTimestamp);
                    kafkaTemplate.send(topicName, partitionKey, modifiedMsg);
                    sentCount++;
                    continue;
                }
            }

            // Default message production
            Message message = new Message(
                    id,
                    "Title " + i,
                    "Random content for message " + i,
                    msgTimestamp);
            kafkaTemplate.send(topicName, partitionKey, message);
            sentCount++;
        }

        // 3. Simulate ADDITION in Index B (Produce completely new keys that did not
        // exist in A)
        if (deltaMode) {
            for (int i = 0; i < 50; i++) {
                String extraId = String.format("msg-extra-%06d", i);
                long extraTimestamp = System.currentTimeMillis() + (1000L * i);
                Message extraMsg = new Message(
                        extraId,
                        "Extra Message Title " + i,
                        "Extra content for message " + i,
                        extraTimestamp);
                kafkaTemplate.send(topicName, extraId, extraMsg);
                sentCount++;
            }
        }

        logger.info("Finished producing {} messages to Kafka.", sentCount);
    }
}
