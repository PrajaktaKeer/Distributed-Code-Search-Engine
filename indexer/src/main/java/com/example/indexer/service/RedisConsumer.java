package com.example.indexer.service;

import com.example.indexer.lucene.LuceneWriter;
import com.example.indexer.model.IndexDocument;
import jakarta.annotation.PostConstruct;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Distributed Redis Stream consumer using XREADGROUP + ACK + PEL claiming.
 * Designed for horizontal scaling and exactly-once idempotent indexing (LuceneWriter.updateDocument).
 */
@Service
public class RedisConsumer {

    private static final Logger log = LoggerFactory.getLogger(RedisConsumer.class);

    private final StringRedisTemplate redisTemplate;
//    private RedisTemplate<String, String> redisTemplate;

    private final LuceneWriter luceneWriter;
    private final ObjectMapper mapper = new ObjectMapper();

    // Config
    private static final String STREAM = "dcse_stream";
    private static final String GROUP = "indexer_group";
    private final String consumerName = "consumer-" + UUID.randomUUID().toString().substring(0, 8);
    private static final Duration BLOCK_MS = Duration.ofSeconds(5);
    private static final int BATCH_SIZE = 20;

    public RedisConsumer(StringRedisTemplate redisTemplate, LuceneWriter luceneWriter) {
        this.redisTemplate = redisTemplate;
        this.luceneWriter = luceneWriter;
    }

    @PostConstruct
    public void start() {
        // Ensure the group exists (create from 0-0 to read backlog the first time)
        ensureGroupExists();

        // First try to recover pending messages that might be stuck
        recoverPendingMessages();

        // Start main consumer thread
        Thread t = new Thread(this::consumeLoop, "redis-consumer-" + consumerName);
        t.setDaemon(false);
        t.start();
        log.info("Started consumer thread {}", consumerName);
    }

    private void ensureGroupExists() {
        try {
            // createGroup will throw if group exists; catch and ignore
            redisTemplate.opsForStream().createGroup(STREAM, ReadOffset.from("0-0"), GROUP);
            log.info("Created consumer group '{}' on stream '{}'", GROUP, STREAM);
        } catch (Exception e) {
            // Many Redis servers will throw if group exists — that's fine
            log.info("Consumer group '{}' already exists or creation failed: {}", GROUP, e.getMessage());
        }
    }

    /**
     * Recovery: check pending list and claim messages that are idle (stalled).
     * This runs once at startup to pick up work left by crashed consumers.
     */
    private void recoverPendingMessages() {

        log.info("🔄 Recovering pending messages via XAUTOCLAIM");

        redisTemplate.execute((RedisCallback<Void>) connection -> {

            byte[] stream = redisTemplate.getStringSerializer().serialize(STREAM);
            byte[] group = redisTemplate.getStringSerializer().serialize(GROUP);
            byte[] consumer = redisTemplate.getStringSerializer().serialize(consumerName);

            String startId = "0-0";
            long minIdleMs = 30_000;

            while (true) {

                // XAUTOCLAIM stream group consumer min-idle start COUNT 10
                Object raw = connection.execute(
                        "XAUTOCLAIM",
                        stream,
                        group,
                        consumer,
                        String.valueOf(minIdleMs).getBytes(),
                        startId.getBytes(),
                        "COUNT".getBytes(),
                        "10".getBytes()
                );

                if (!(raw instanceof List<?> result) || result.size() < 2) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Object> messages = (List<Object>) result.get(1);

                if (messages.isEmpty()) {
                    break;
                }

                int docCount = 0;

                for (Object entry : messages) {
                    docCount++;
                    @SuppressWarnings("unchecked")
                    List<Object> msg = (List<Object>) entry;

                    String id = new String((byte[]) msg.get(0));
                    @SuppressWarnings("unchecked")
                    List<Object> fields = (List<Object>) msg.get(1);

                    // Convert Redis fields → Map
                    MapRecord<String, Object, Object> record =
                            StreamRecords.<String, Object, Object>mapBacked(toMap(fields))
                                    .withId(RecordId.of(id));

                    try {
                        processRecord(record, docCount);
                        redisTemplate.opsForStream().acknowledge(STREAM, GROUP, record.getId());
                    } catch (Exception e) {
                        log.error("Failed processing recovered msg {}", id, e);
                    }
                }

                startId = new String((byte[]) result.get(0));
            }

            return null;
        });
    }

    private Map<Object, Object> toMap(List<Object> fields) {
        Map<Object, Object> map = new HashMap<>();
        for (int i = 0; i < fields.size(); i += 2) {
            map.put(fields.get(i), fields.get(i + 1));
        }
        return map;
    }

    /**
     * Main consumer loop: XREADGROUP with blocking and batch size.
     */
    private void consumeLoop() {
        log.info("Consumer {} joining group {} on stream {}", consumerName, GROUP, STREAM);

        Consumer consumer = Consumer.from(GROUP, consumerName);
        StreamReadOptions options = StreamReadOptions.empty()
                .count(BATCH_SIZE)
                .block(BLOCK_MS);

        // We read last-consumed so consumer group gives us messages assigned to this consumer
        StreamOffset<String> offset = StreamOffset.create(STREAM, ReadOffset.lastConsumed());

        while (true) {
            try {
                List<@NonNull MapRecord<String, Object, Object>> msgs =
                        redisTemplate.opsForStream().read(consumer, options, offset);

                if (msgs == null || msgs.isEmpty()) {
                    // no messages this cycle
                    continue;
                }

                int docCount = 0;

                for (@NonNull MapRecord<String, Object, Object> msg : msgs) {
                    try {
                        docCount++;
                        processRecord(msg, docCount);
                        // Acknowledge after successful indexing
                        redisTemplate.opsForStream().acknowledge(STREAM, GROUP, msg.getId());
                    } catch (Exception e) {
                        log.error("Failed processing message id {}: {} — will not ack to allow retry", msg.getId(), e.getMessage(), e);
                        // Do NOT ack: leave it pending so claim/retry can pick it up
                    }
                }
            } catch (Exception e) {
                log.error("Error in consumer loop: {}", e.getMessage(), e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Convert message → IndexDocument → idempotent lucene updateDocument
     */
    private void processRecord(@NonNull MapRecord<String, Object, Object> msg, int docCount) throws Exception {
        Object raw = msg.getValue().get("doc");
        if (raw == null) {
            log.warn("Message {} has no 'doc' field, skipping", msg.getId());
            // acknowledge to skip bad messages (optional)
            redisTemplate.opsForStream().acknowledge(STREAM, GROUP, msg.getId());
            return;
        }

        String json = raw.toString();
        IndexDocument doc = mapper.readValue(json, IndexDocument.class);

        log.info("Indexing doc {} id={} path={}", docCount, doc.getId(), doc.getPath());

        // idempotent update to avoid duplicates
        luceneWriter.updateDocument(doc);

    }
}
