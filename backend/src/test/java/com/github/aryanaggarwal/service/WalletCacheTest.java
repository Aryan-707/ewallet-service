package com.github.aryanaggarwal.service;

import com.github.aryanaggarwal.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletCacheTest {

    @InjectMocks
    private BalanceService balanceService;

    @Mock
    private RedisService redisService;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void getBalance_shouldReturnFromCacheIfPresent() {
        Long walletId = 1L;
        BigDecimal cachedBalance = BigDecimal.valueOf(100);

        when(redisService.getCachedBalance(walletId)).thenReturn(cachedBalance);

        BigDecimal result = balanceService.getBalance(walletId);

        assertEquals(cachedBalance, result);
        verify(ledgerEntryRepository, never()).getBalance(anyLong());
        verify(redisService, never()).cacheBalance(anyLong(), any());
    }

    @Test
    void getBalance_shouldComputeAndCacheIfMissingInRedis() {
        Long walletId = 1L;
        BigDecimal computedBalance = BigDecimal.valueOf(200);

        when(redisService.getCachedBalance(walletId)).thenReturn(null);
        when(ledgerEntryRepository.getBalance(walletId)).thenReturn(computedBalance);

        BigDecimal result = balanceService.getBalance(walletId);

        assertEquals(computedBalance, result);
        verify(redisService).cacheBalance(walletId, computedBalance);
    }
}
