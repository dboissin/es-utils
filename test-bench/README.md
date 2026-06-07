# Kafka-to-Elasticsearch Spring Boot Test Bench

This sub-project is a local test bench built to validate the parent Rust-based Elasticsearch diff index tool. It generates mock data, streams it through a partitioned Kafka topic, and indices it into Elasticsearch using a Spring Boot application.

---

## Design Intentions

### 1. Performance Considerations
- **Partitioned Concurrency**: The Kafka topic is provisioned with **5 partitions**. The Spring consumer listener container matches this with **5 concurrent threads**, ensuring that each thread is exclusively assigned to one partition for parallel processing.
- **Batch Consumption**: The `@KafkaListener` is configured to poll records in batches (up to 500 records) rather than one by one, reducing the overhead of polling loops and locking mechanisms.
- **Bulk Indexing**: We demonstrate two ways to bulk index messages into Elasticsearch using Spring Data ES:
  - **Approach A (Operations)**: Accumulates the batch of messages into a list of `IndexQuery` objects and sends them in a single bulk HTTP request via `ElasticsearchOperations.bulkIndex()`.
  - **Approach B (Repository)**: Uses `MessageRepository.saveAll()`, which automatically batches entities and delegates to a bulk indexing payload under the hood.

### 2. Consistency Considerations
- **At-Least-Once Delivery**: The Kafka offset commit mode is set to manual (`AckMode.MANUAL_IMMEDIATE`). The offset is committed **only** after Elasticsearch acknowledges a successful bulk write. If the write fails, the commit is skipped, causing the Kafka consumer to seek back and retry the batch.
- **Idempotency**: Message business IDs are bound directly to the Elasticsearch document `_id`. In the event of a crash and retry (replayed offsets), Elasticsearch performs an overwrite rather than creating duplicate documents.
- **Strict Ordering**: By using the message business ID as the partition routing key, all events corresponding to a specific key are guaranteed to go to the same Kafka partition and thus are processed sequentially in their exact ingestion order.

---

## Prerequisites
Ensure you have the following installed on your machine:
- Java 21 (OpenJDK)
- Maven 3.8+
- Docker and Docker Compose
- Rust/Cargo (for running the parent diff index tool)

---

## Step-by-Step Commands

### 1. Spin Up Docker Infrastructure
Run the following command inside the `test-bench/` folder to start Elasticsearch, Kibana, Kafka (KRaft), and Kafka-UI:
```bash
docker compose up -d
```
Verify they are running and healthy:
```bash
docker compose ps
```
Services available:
- **Elasticsearch**: `http://localhost:9200` (Security enabled: `elastic` / `changeme`)
- **Kibana**: `http://localhost:5601` (Security enabled, login with `elastic` / `changeme`)
- **Kafka**: `localhost:9092`
- **Kafka-UI**: `http://localhost:8080`

### 2. Configure Elasticsearch Cluster
Since security is enabled in Elasticsearch 8.x, you must initialize the `kibana_system` user password to enable Kibana to connect, and enable fielddata sorting on the `_id` field.

Run these two commands:
```bash
# 1. Set the password for the built-in kibana_system user (used by the Kibana service)
curl -u elastic:changeme -X POST "http://localhost:9200/_security/user/kibana_system/_password" \
     -H "Content-Type: application/json" \
     -d '{"password":"changeme"}'

# 2. Enable fielddata sorting on _id (required for the Rust diff tool's search-after cursor)
curl -u elastic:changeme -X PUT "http://localhost:9200/_cluster/settings" \
     -H 'Content-Type: application/json' \
     -d '{"persistent": {"indices.id_field_data.enabled": true}}'
```

### 3. Build the Spring Boot App
Navigate to the `spring-app/` folder and compile the project:
```bash
cd spring-app
mvn clean package -DskipTests
```

### 4. Populate Index A (Baseline Data)
Run the application to produce and consume 10,000 standard messages into Elasticsearch index `mon_index_a`:
```bash
java -jar target/test-bench-app-0.0.1-SNAPSHOT.jar \
  --app.elasticsearch.index-name=mon_index_a \
  --app.producer.delta-mode=false
```
*Note: The application will automatically shutdown once 10,000 messages have been processed.*

### 5. Populate Index B (Delta Data)
Run the application again to populate `mon_index_b`, simulating modifications, deletions, and additions:
```bash
java -jar target/test-bench-app-0.0.1-SNAPSHOT.jar \
  --app.elasticsearch.index-name=mon_index_b \
  --app.producer.delta-mode=true
```
