package com.github.aryanaggarwal.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate redisTemplate;

    public void cacheBalance(Long walletId, BigDecimal balance) {
        String key = "wallet:" + walletId + ":balance";
        redisTemplate.opsForValue().set(key, balance.toString(), 1, TimeUnit.HOURS);
    }

    public BigDecimal getCachedBalance(Long walletId) {
        String key = "wallet:" + walletId + ":balance";
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? new BigDecimal(value) : null;
    }

    public void evictBalanceCache(Long walletId) {
        String key = "wallet:" + walletId + ":balance";
        redisTemplate.delete(key);
    }

    public boolean isRateLimited(Long userId) {
        String key = "rate_limit:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) return false;
        if (count == 1) {
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        return count > 5;
    }
}
