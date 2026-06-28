package dev.ceven.testbench.application.ports.in;

import dev.ceven.testbench.domain.model.Message;
import java.util.List;

public interface ProcessMessageUseCase {
    void processAndIndex(List<Message> messages);
}
