package com.whxbill.backend.common.cache;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisCacheSupport {

    public static final String NULL_MARKER = "__NULL__";

    private final StringRedisTemplate stringRedisTemplate;

    public Duration withRandomSeconds(Duration base, int randomSeconds) {
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(randomSeconds + 1));
    }

    public boolean tryLock(String key, Duration duration) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", duration));
    }

    public void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    public void deleteByPattern(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
