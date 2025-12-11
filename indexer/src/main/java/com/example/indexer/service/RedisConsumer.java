package com.example.indexer.service;

import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisConsumer {

    private final StringRedisTemplate redisTemplate;

    public RedisConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
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

        while (true) {
            try {
                List<MapRecord<String, Object, Object>> msgs =
                        redisTemplate.opsForStream().read(
                                StreamReadOptions.empty().block(Duration.ofSeconds(5)),
                                StreamOffset.fromStart("dcse_stream")
                        );

                if (msgs != null) {
                    for (MapRecord<String, Object, Object> msg : msgs) {
                        msg.getValue().forEach((k, v) ->
                                System.out.println("📥 " + k + "=" + v)
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Thread.sleep(1000);
            }
        }
    }
}
