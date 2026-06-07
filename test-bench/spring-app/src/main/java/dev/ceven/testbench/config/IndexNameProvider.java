package dev.ceven.testbench.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("indexNameProvider")
public class IndexNameProvider {

    @Value("${app.elasticsearch.index-name:mon_index_a}")
    private String indexName;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }
}
