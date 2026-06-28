package dev.ceven.testbench.infrastructure.adapters.out.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SpringDataElasticsearchMessageRepository extends ElasticsearchRepository<MessageDocument, String> {
}
