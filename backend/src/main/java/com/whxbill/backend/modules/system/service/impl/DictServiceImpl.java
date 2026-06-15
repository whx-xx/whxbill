package com.whxbill.backend.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whxbill.backend.common.cache.RedisCacheSupport;
import com.whxbill.backend.modules.system.entity.SysDict;
import com.whxbill.backend.modules.system.mapper.SysDictMapper;
import com.whxbill.backend.modules.system.service.DictService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);

    private final SysDictMapper sysDictMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheSupport redisCacheSupport;

    @Override
    public List<SysDict> listByType(String dictType) {
        String cacheKey = "whx:bill:dict:" + dictType;
        String lockKey = "whx:bill:lock:dict:" + dictType;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (RedisCacheSupport.NULL_MARKER.equals(cached)) {
                return List.of();
            }
            try {
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
            }
        }
        boolean locked = redisCacheSupport.tryLock(lockKey, LOCK_TTL);
        try {
            if (!locked) {
                try {
                    Thread.sleep(120L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return listByType(dictType);
            }

            List<SysDict> dicts = sysDictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, dictType)
                .eq(SysDict::getStatus, 1)
                .orderByAsc(SysDict::getSortOrder));
            try {
                if (dicts.isEmpty()) {
                    stringRedisTemplate.opsForValue().set(cacheKey, RedisCacheSupport.NULL_MARKER,
                        redisCacheSupport.withRandomSeconds(Duration.ofMinutes(5), 30));
                } else {
                    stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(dicts),
                        redisCacheSupport.withRandomSeconds(CACHE_TTL, 180));
                }
            } catch (Exception ignored) {
            }
            return dicts;
        } finally {
            if (locked) {
                redisCacheSupport.unlock(lockKey);
            }
        }
    }
}
