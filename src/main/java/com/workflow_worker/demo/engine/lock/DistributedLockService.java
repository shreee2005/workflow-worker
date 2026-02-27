package com.workflow_worker.demo.engine.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class DistributedLockService {
    private final StringRedisTemplate redisTemplate;

    public DistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquire(UUID runId){
        String key = "workflow:lock" + runId;
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key , "locked" , Duration.ofMinutes(10));
        return Boolean.TRUE.equals(success);
    }

    public void release(UUID runId){
        String key = "workflow:lock" + runId;
        redisTemplate.delete(key);
    }
}
