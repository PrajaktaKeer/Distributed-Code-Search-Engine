package com.example.indexer.api;

import com.example.indexer.lucene.LuceneWriter;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final StringRedisTemplate redisTemplate;
    private final LuceneWriter luceneWriter;

    public HealthController(StringRedisTemplate redisTemplate, LuceneWriter luceneWriter) {
        this.redisTemplate = redisTemplate;
        this.luceneWriter = luceneWriter;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() throws Exception {
        Map<String, Object> out = new HashMap<>();
        boolean redisOk = true;
        try { redisTemplate.getConnectionFactory().getConnection().ping(); } catch (Exception e) { redisOk = false; }
        out.put("redisConnected", redisOk);

        PendingMessagesSummary pms = redisTemplate.opsForStream().pending("dcse_stream", "indexer_group");
        out.put("pendingTotal", pms == null ? 0 : pms.getTotalPendingMessages());

        out.put("indexedDocs", luceneWriter.getNumDocs());
        return out;
    }
}
