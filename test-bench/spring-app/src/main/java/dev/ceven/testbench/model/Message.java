package dev.ceven.testbench.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "#{@indexNameProvider.getIndexName()}")
public record Message(
        @Id String id,

        @Field(type = FieldType.Keyword) String title,

        @Field(type = FieldType.Text) String content,

        @Field(type = FieldType.Long) long timestamp) {
}
