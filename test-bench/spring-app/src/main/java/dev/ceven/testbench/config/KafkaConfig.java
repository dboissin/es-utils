package dev.ceven.testbench.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import dev.ceven.testbench.model.Message;

@Configuration
public class KafkaConfig {

    @Value("${app.topic-name}")
    private String topicName;

    @Bean
    public NewTopic messagesTopic() {
        // Creates the topic with 5 partitions and 1 replica on startup
        return TopicBuilder.name(topicName)
                .partitions(5)
                .replicas(1)
                .build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> kafkaConsumerFactory) {

        ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, kafkaConsumerFactory);

        // Performance: Enable batch listener to process records in bulk
        factory.setBatchListener(true);

        // Performance: Set concurrency to 5 (matches the number of partitions)
        factory.setConcurrency(5);

        // Consistency: Set manual offset commit (AckMode.MANUAL_IMMEDIATE)
        // This allows us to acknowledge offsets only after successfully saving to
        // Elasticsearch
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
