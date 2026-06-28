package dev.ceven.testbench.application.ports.out;

import dev.ceven.testbench.domain.model.Message;
import java.util.List;

public interface MessageRepositoryPort {
    List<Message> findByIds(List<String> ids);
    void saveAll(List<Message> messages);
}
