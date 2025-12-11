package com.example.indexer.service;

import com.example.indexer.lucene.LuceneWriter;
import com.example.indexer.model.IndexDocument;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

@Service
public class RedisConsumer {

    private final StringRedisTemplate redisTemplate;
    private final LuceneWriter luceneWriter;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String STREAM = "dcse_stream";
    private static final String GROUP = "indexer_group";
    private static final String CONSUMER = "consumer-1";

    public RedisConsumer(StringRedisTemplate redisTemplate, LuceneWriter luceneWriter) {
        this.redisTemplate = redisTemplate;
        this.luceneWriter = luceneWriter;
    }


    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            try {
                consume();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.setDaemon(false);
        t.start();
    }

    private void consume() throws InterruptedException {
        System.out.println("🔥 Consumer running...");

        long docCounter = 0;

        while (true) {
            try {
//                List<MapRecord<String, Object, Object>> msgs =
//                        redisTemplate.opsForStream().read(
//                                StreamReadOptions.empty().block(Duration.ofSeconds(5)),
//                                StreamOffset.latest("dcse_stream")
//                        );
                List<MapRecord<String, Object, Object>> msgs =
                        redisTemplate.opsForStream().read(
                                Consumer.from(GROUP, CONSUMER),
                                StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
                                StreamOffset.create(STREAM, ReadOffset.lastConsumed())
                        );

                if (msgs != null) {
                    for (MapRecord<String, Object, Object> msg : msgs) {
                        docCounter++;
//                        msg.getValue().forEach((k, v) ->
//                                System.out.println("📥 " + k + "=" + v)
//                        );

                        String json = (String) msg.getValue().get("doc");
                        IndexDocument doc = mapper.readValue(json, IndexDocument.class);

                        System.out.println("📥 Received doc " + docCounter + " : "  + doc.getPath());

                        luceneWriter.addDocument(doc);
                        System.out.println("📚 Indexed into Lucene.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(1000);
            }
        }
    }
}
