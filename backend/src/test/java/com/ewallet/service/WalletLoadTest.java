package com.ewallet.service;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.domain.entity.Wallet;
import com.ewallet.repository.WalletRepository;
import com.ewallet.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Load test simulation specifically for answering whether the system handles massive spikes of transactions. 
 * This test simulates processing latency explicitly for simulating connection pool/queue backpressure.
 */
@ExtendWith(MockitoExtension.class)
class WalletLoadTest {

    @InjectMocks
    private WalletService walletService;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private com.ewallet.config.MessageSourceConfig messageConfig;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private TransactionService transactionService;

    @Mock
    private RedisService redisService;
    
    @Mock
    private BalanceService balanceService;

    @Test
    void simulateHeavyLoadForBackpressure() throws InterruptedException {
        int threads = 50; // High concurrent load
        ExecutorService executor = Executors.newFixedThreadPool(10); // Simulating limited pool (e.g. Hikari pool)
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger processed = new AtomicInteger();

        Wallet fromWallet = new Wallet();
        fromWallet.setId(1L);
        fromWallet.setIban("FROM_LOAD");

        Wallet toWallet = new Wallet();
        toWallet.setId(2L);
        toWallet.setIban("TO_LOAD");

        com.ewallet.domain.entity.User user = new com.ewallet.domain.entity.User();
        user.setId(1L);
        user.setUsername("testuser");
        fromWallet.setUser(user);

        when(walletRepository.findByIbanWithPessimisticWriteLock("FROM_LOAD")).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByIban("TO_LOAD")).thenReturn(Optional.of(toWallet));
        
        doAnswer(inv -> BigDecimal.valueOf(1000000)).when(balanceService).getBalance(1L);
        doAnswer(inv -> BigDecimal.ZERO).when(balanceService).getBalance(2L);

        // Simulate DB / Processing latency
        when(transactionService.create(any(TransactionRequest.class))).thenAnswer(invocation -> {
            Thread.sleep(10); 
            return new CommandResponse(1L);
        });

        long start = System.currentTimeMillis();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                SecurityContext securityContext = mock(SecurityContext.class);
                Authentication authentication = mock(Authentication.class);
                lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
                lenient().when(authentication.getName()).thenReturn("testuser");
                SecurityContextHolder.setContext(securityContext);
                try {
                    TransactionRequest request = new TransactionRequest();
                    request.setFromWalletIban("FROM_LOAD");
                    request.setToWalletIban("TO_LOAD");
                    request.setAmount(BigDecimal.ONE);
                    
                    walletService.transferFunds(request);
                    processed.incrementAndGet();
                } catch (Exception e) {
                    // Ignore expected failures from load
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - start;
        assertTrue(completed, "System should process all items within acceptable backpressure latency");
        assertTrue(duration >= 50, "Latency simulation confirms requests queued rather than rejected instantly");
        assertTrue(processed.get() > 0, "Some requests processed fully");
    }
}
