package dev.ceven.testbench.infrastructure.config;

import dev.ceven.testbench.domain.model.Message;
import dev.ceven.testbench.domain.mapping.FieldRule;
import dev.ceven.testbench.application.ports.out.MessageRepositoryPort;
import dev.ceven.testbench.application.service.MessageProcessorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class DomainConfig {

    @Bean
    public MessageProcessorService messageProcessorService(MessageRepositoryPort repositoryPort) {
        List<FieldRule<Message, Message>> rules = new ArrayList<>();

        // Specification & Mapping Rules:

        // 1. ID Rule: If ID has spaces, replace with hyphens
        rules.add(new FieldRule<>(
                msg -> msg.id() != null && msg.id().contains(" "),
                msg -> msg.withId(msg.id().replace(" ", "-"))
        ));

        // 2. Title Rule: If Title contains "Modified", append "[PROCESSED_MOD]"
        rules.add(new FieldRule<>(
                msg -> msg.title() != null && msg.title().contains("Modified"),
                msg -> msg.withTitle(msg.title() + " [PROCESSED_MOD]")
        ));

        // 3. Title Rule: If Title is null or empty, assign a default
        rules.add(new FieldRule<>(
                msg -> msg.title() == null || msg.title().isBlank(),
                msg -> msg.withTitle("Untitled Message")
        ));

        // 4. Content Rule: Mask sensitive information (contains case-insensitive "sensitive")
        rules.add(new FieldRule<>(
                msg -> msg.content() != null && msg.content().toLowerCase().contains("sensitive"),
                msg -> msg.withContent(msg.content().replaceAll("(?i)sensitive", "****"))
        ));

        // 5. Content Rule: If Content is null or empty, assign a default
        rules.add(new FieldRule<>(
                msg -> msg.content() == null || msg.content().isBlank(),
                msg -> msg.withContent("No content provided.")
        ));

        // 6. Timestamp Rule: If Timestamp is zero or negative, set to current system time
        rules.add(new FieldRule<>(
                msg -> msg.timestamp() <= 0,
                msg -> msg.withTimestamp(System.currentTimeMillis())
        ));

        return new MessageProcessorService(repositoryPort, rules);
    }
}
