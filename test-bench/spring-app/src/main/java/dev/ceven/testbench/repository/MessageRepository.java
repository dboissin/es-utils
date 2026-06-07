package dev.ceven.testbench.repository;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import dev.ceven.testbench.model.Message;

@Repository
public interface MessageRepository extends ElasticsearchRepository<Message, String> {
}
