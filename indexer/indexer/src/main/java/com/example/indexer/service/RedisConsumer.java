package com.example.indexer.service;

import com.example.indexer.lucene.LuceneIndexService;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class RedisConsumer {

    private final RedisTemplate<String, String> redisTemplate;
    private final LuceneIndexService luceneIndexService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisConsumer(RedisTemplate<String, String> redisTemplate,
                         LuceneIndexService luceneIndexService) {
        this.redisTemplate = redisTemplate;
        this.luceneIndexService = luceneIndexService;
    }

    @PostConstruct
    public void start() {
        new Thread(this::consume).start();
    }

    private void consume() {
        System.out.println("Redis consumer started...");
        String stream = "dcse_stream";

        while (true) {
            try {
                List<MapRecord<String, Object, Object>> msgs =
                        redisTemplate.opsForStream().read(
                                StreamReadOptions.empty().block(Duration.ofSeconds(1)),
                                StreamOffset.fromStart(stream)
                        );

                if (msgs != null) {
                    for (MapRecord<String, Object, Object> msg : msgs) {

                        String json = msg.getValue().get("doc").toString();
                        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

                        luceneIndexService.indexDocument(parsed);

                        System.out.println("Indexed: " + parsed.get("path"));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
