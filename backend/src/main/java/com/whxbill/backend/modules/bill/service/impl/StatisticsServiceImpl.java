package com.whxbill.backend.modules.bill.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whxbill.backend.common.cache.RedisCacheSupport;
import com.whxbill.backend.modules.bill.dto.StatisticsQuery;
import com.whxbill.backend.modules.bill.mapper.BizBillMapper;
import com.whxbill.backend.modules.bill.service.StatisticsService;
import com.whxbill.backend.security.SecurityUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    public static final String DASHBOARD_CACHE_PREFIX = "whx:bill:dashboard:v2:";

    private static final Duration CACHE_TTL = Duration.ofMinutes(20);
    private static final Duration LOCK_TTL = Duration.ofSeconds(8);

    private final BizBillMapper bizBillMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisCacheSupport redisCacheSupport;

    /**
     * 兼容旧调用：只传月份时，默认按月统计。
     */
    @Override
    public Map<String, Object> getDashboard(String month) {
        StatisticsQuery query = new StatisticsQuery();
        query.setMonth(month);
        query.setMode("month");
        return getDashboard(query);
    }

    /**
     * 统计仪表盘主方法：按查询条件返回汇总、分类统计和趋势数据，并使用 Redis 缓存。
     */
    @Override
    public Map<String, Object> getDashboard(StatisticsQuery query) {
        DateRange range = resolveDateRange(query);
        boolean includeChildren = Boolean.TRUE.equals(query.getIncludeChildren());
        // 缓存维度包含用户、账本、时间范围和是否合并子分类，避免不同统计条件互相污染。
        String cacheKey = DASHBOARD_CACHE_PREFIX + SecurityUtils.getUserId() + ":" + query.getBookId() + ":"
            + range.startDate() + ":" + range.endDate() + ":" + includeChildren;
        String lockKey = "whx:bill:lock:dashboard:" + SecurityUtils.getUserId() + ":" + query.getBookId() + ":"
            + range.startDate() + ":" + range.endDate() + ":" + includeChildren;
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            if (RedisCacheSupport.NULL_MARKER.equals(cached)) {
                return Map.of("summary", Map.of(), "expenseCategories", java.util.List.of(), "incomeCategories", java.util.List.of(), "trend", java.util.List.of());
            }
            try {
                // 命中缓存时直接把 JSON 还原成 Map，减少统计 SQL 压力。
                return objectMapper.readValue(cached, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // 缓存反序列化失败就忽略，重新查数据库并覆盖缓存。
            }
        }
        // 加锁，防止缓存击穿，如果很多请求同时查同一份统计数据，只有一个请求真正查数据库，其他请求稍等后读缓存
        boolean locked = redisCacheSupport.tryLock(lockKey, LOCK_TTL);
        try {
            if (!locked) {
                try {
                    // 没抢到锁的请求稍等片刻，让抢到锁的请求先把结果写入缓存。
                    Thread.sleep(120L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return getDashboard(query);
            }

            Map<String, Object> result = new HashMap<>();
            // 仪表盘一次返回总览、分类占比和趋势数据，前端统计页可以一次请求完成渲染。
            result.put("summary", bizBillMapper.selectSummary(SecurityUtils.getUserId(), query.getBookId(), range.startDate(), range.endDate()));
            result.put("expenseCategories", bizBillMapper.selectCategoryStats(SecurityUtils.getUserId(), query.getBookId(),
                "EXPENSE", range.startDate(), range.endDate(), includeChildren));
            result.put("incomeCategories", bizBillMapper.selectCategoryStats(SecurityUtils.getUserId(), query.getBookId(),
                "INCOME", range.startDate(), range.endDate(), includeChildren));
            result.put("categories", result.get("expenseCategories"));
            result.put("trend", "year".equalsIgnoreCase(query.getMode())
                ? bizBillMapper.selectTrendByMonth(SecurityUtils.getUserId(), query.getBookId(), range.startDate(), range.endDate())
                : bizBillMapper.selectTrendByDay(SecurityUtils.getUserId(), query.getBookId(), range.startDate(), range.endDate()));
            try {
                stringRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    redisCacheSupport.withRandomSeconds(CACHE_TTL, 180));
            } catch (Exception ignored) {
                // 缓存写入失败不影响接口返回，最多下一次重新查数据库。
            }
            return result;
        } finally {
            if (locked) {
                redisCacheSupport.unlock(lockKey);
            }
        }
    }

    /**
     * 根据统计模式计算日期范围：年、月、自定义区间三种。
     */
    private DateRange resolveDateRange(StatisticsQuery query) {
        String mode = query.getMode() == null ? "month" : query.getMode();
        if ("year".equalsIgnoreCase(mode)) {
            int year = query.getYear() == null || query.getYear().isBlank() ? LocalDate.now().getYear() : Integer.parseInt(query.getYear());
            return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        if ("custom".equalsIgnoreCase(mode) && query.getStartDate() != null && query.getEndDate() != null) {
            return new DateRange(query.getStartDate(), query.getEndDate());
        }
        YearMonth month = query.getMonth() == null || query.getMonth().isBlank()
            ? YearMonth.now()
            : YearMonth.parse(query.getMonth());
        return new DateRange(month.atDay(1), month.atEndOfMonth());
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
