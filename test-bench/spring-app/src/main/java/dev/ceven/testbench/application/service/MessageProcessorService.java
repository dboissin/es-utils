package dev.ceven.testbench.application.service;

import dev.ceven.testbench.domain.model.Message;
import dev.ceven.testbench.domain.model.IngestionMetric;
import dev.ceven.testbench.domain.mapping.FieldRule;
import dev.ceven.testbench.application.ports.in.ProcessMessageUseCase;
import dev.ceven.testbench.application.ports.out.MessageRepositoryPort;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MessageProcessorService implements ProcessMessageUseCase {

    private final MessageRepositoryPort repositoryPort;
    private final List<FieldRule<Message, Message>> rules;

    public MessageProcessorService(
            MessageRepositoryPort repositoryPort,
            List<FieldRule<Message, Message>> rules) {
        this.repositoryPort = repositoryPort;
        this.rules = rules;
    }

    @Override
    public void processAndIndex(List<Message> messages) {
        List<String> ids = messages.stream()
                .map(Message::id)
                .toList();

        // 1. Fetch existing versions for optimistic locking
        List<Message> existingMessages = repositoryPort.findByIds(ids);
        Map<String, Message> existingMap = existingMessages.stream()
                .collect(Collectors.toMap(Message::id, m -> m, (m1, m2) -> m1));

        // 2. Apply field rules and set version
        List<Message> processed = messages.stream()
                .map(msg -> {
                    Message modified = applyRules(msg);
                    Message existing = existingMap.get(modified.id());
                    Long seqNo = existing != null ? existing.seqNo() : null;
                    Long primaryTerm = existing != null ? existing.primaryTerm() : null;
                    List<IngestionMetric> existingHistory = existing != null ? existing.ingestionHistory() : List.of();
                    return new Message(
                            modified.id(),
                            modified.title(),
                            modified.content(),
                            modified.timestamp(),
                            seqNo,
                            primaryTerm,
                            msg.processingStartTime(),
                            existingHistory
                    );
                })
                .toList();

        // 3. Save modified messages
        repositoryPort.saveAll(processed);
    }

    private Message applyRules(Message original) {
        Message current = original;
        for (FieldRule<Message, Message> rule : rules) {
            if (rule.isSatisfiedBy(current)) {
                current = rule.apply(current);
            }
        }
        return current;
    }
}
