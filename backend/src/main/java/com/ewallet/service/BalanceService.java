package com.ewallet.service;

import com.ewallet.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BalanceService {

    private final RedisService redisService;
    private final LedgerEntryRepository ledgerEntryRepository;

    public BigDecimal getBalance(Long walletId) {
        BigDecimal cached = redisService.getCachedBalance(walletId);
        if (cached != null) {
            return cached;
        }

        BigDecimal computed = ledgerEntryRepository.getBalance(walletId);
        if (computed == null) {
            computed = BigDecimal.ZERO;
        }

        redisService.cacheBalance(walletId, computed);
        return computed;
    }
}
