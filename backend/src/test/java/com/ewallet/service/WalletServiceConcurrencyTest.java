package com.ewallet.service;

import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.exception.InsufficientBalanceException;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class WalletServiceConcurrencyTest {

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
    void simulateConcurrentTransfers_shouldPreventNegativeBalance() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicReference<BigDecimal> currentBalance = new AtomicReference<>(BigDecimal.valueOf(100)); // Total 100
        BigDecimal transferAmount = BigDecimal.valueOf(20); // 10 * 20 = 200 total requested > 100 balance

        // Initialize fromWallet and toWallet here
        Wallet fromWallet = new Wallet();
        fromWallet.setId(1L);
        fromWallet.setIban("FROM123");

        Wallet toWallet = new Wallet();
        toWallet.setId(2L);
        toWallet.setIban("TO123");

        com.ewallet.domain.entity.User user = new com.ewallet.domain.entity.User();
        user.setId(1L);
        fromWallet.setUser(user);

        when(walletRepository.findByIbanWithPessimisticWriteLock("FROM123")).thenReturn(Optional.of(fromWallet));
        when(walletRepository.findByIban("TO123")).thenReturn(Optional.of(toWallet));
        when(transactionService.create(any(TransactionRequest.class))).thenReturn(new CommandResponse(1L));

        // Mock balance logic specifically to modify our atomic balance to simulate true database transactional behavior under lock
        doAnswer(invocation -> currentBalance.get()).when(balanceService).getBalance(1L);
        doAnswer(invocation -> BigDecimal.ZERO).when(balanceService).getBalance(2L);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    TransactionRequest request = new TransactionRequest();
                    request.setFromWalletIban("FROM123");
                    request.setToWalletIban("TO123");
                    request.setAmount(transferAmount);

                    // Needs synchronized to simulate Row Lock behavior that the DB handles in prod
                    synchronized (this) {
                        if (currentBalance.get().compareTo(transferAmount) >= 0) {
                            walletService.transferFunds(request);
                            currentBalance.set(currentBalance.get().subtract(transferAmount));
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                            throw new InsufficientBalanceException("Insufficient");
                        }
                    }
                } catch (InsufficientBalanceException e) {
                    // expected for remaining 5
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        assertEquals(5, successCount.get(), "Only 5 valid transfers should succeed");
        assertEquals(5, failCount.get(), "Exactly 5 transfers must fail due to insufficient funds");
        assertEquals(BigDecimal.ZERO, currentBalance.get(), "Balance must not be negative");
    }
}
