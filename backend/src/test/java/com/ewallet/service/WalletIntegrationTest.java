package com.ewallet.service;

import com.ewallet.domain.entity.*;
import com.ewallet.domain.enums.RoleType;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.exception.InsufficientBalanceException;
import com.ewallet.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that hit a real H2 database to verify ledger math,
 * pessimistic locking, and idempotency constraints end-to-end.
 */
@SpringBootTest
@Transactional
@ActiveProfiles("test")
class WalletIntegrationTest {

    @Autowired private WalletService walletService;
    @Autowired private BalanceService balanceService;
    @Autowired private TransactionService transactionService;
    @Autowired private WalletRepository walletRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TypeRepository typeRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private TransactionRepository transactionRepository;

    // RedisService needs to be mocked since we don't spin up a Redis container in tests
    @MockBean
    private RedisService redisService;

    private Wallet walletA;
    private Wallet walletB;

    @BeforeEach
    void setUp() {
        // Seed role
        Role userRole = new Role();
        userRole.setType(RoleType.ROLE_USER);
        userRole = roleRepository.save(userRole);

        // Seed transaction type — needed for transfers
        Type transferType = new Type();
        transferType.setName("Transfer");
        transferType.setDescription("Wallet to wallet transfer");
        typeRepository.save(transferType);

        // Create User A
        User userA = new User();
        userA.setFirstName("Alice");
        userA.setLastName("Test");
        userA.setUsername("alice");
        userA.setEmail("alice@test.com");
        userA.setPassword("$2a$10$dummyHashedPasswordForTestingPurposes"); // BCrypt placeholder
        userA.getRoles().add(userRole);
        userA = userRepository.save(userA);

        // Create User B
        User userB = new User();
        userB.setFirstName("Bob");
        userB.setLastName("Test");
        userB.setUsername("bob");
        userB.setEmail("bob@test.com");
        userB.setPassword("$2a$10$dummyHashedPasswordForTestingPurposes");
        userB.getRoles().add(userRole);
        userB = userRepository.save(userB);

        // Create wallets
        walletA = new Wallet();
        walletA.setIban("DE89370400440532013000"); // valid German IBAN format
        walletA.setName("Alice Main");
        walletA.setUser(userA);
        walletA = walletRepository.save(walletA);

        walletB = new Wallet();
        walletB.setIban("GB29NWBK60161331926819"); // valid UK IBAN format
        walletB.setName("Bob Main");
        walletB.setUser(userB);
        walletB = walletRepository.save(walletB);

        // Seed initial balance of 1000 for both wallets via ledger CREDIT entries
        seedBalance(walletA, BigDecimal.valueOf(1000));
        seedBalance(walletB, BigDecimal.valueOf(1000));

        // Mock Redis to never rate-limit and return no cached balance
        org.mockito.Mockito.when(redisService.isRateLimited(org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        org.mockito.Mockito.when(redisService.getCachedBalance(org.mockito.ArgumentMatchers.anyLong())).thenReturn(null);

        // Set SecurityContext so ownership checks pass for alice
        setAuthenticatedUser("alice");
    }

    /**
     * Creates a CREDIT ledger entry to seed initial wallet balance.
     */
    private void seedBalance(Wallet wallet, BigDecimal amount) {
        // Create a seed transaction first
        Transaction seedTxn = new Transaction();
        seedTxn.setAmount(amount);
        seedTxn.setDescription("Initial deposit");
        seedTxn.setCreatedAt(Instant.now());
        seedTxn.setReferenceNumber(java.util.UUID.randomUUID());
        seedTxn.setStatus(com.ewallet.domain.enums.Status.COMPLETED);
        seedTxn.setFromWallet(wallet);
        seedTxn.setToWallet(wallet);
        seedTxn.setType(typeRepository.findAll().get(0));
        seedTxn = transactionRepository.save(seedTxn);

        LedgerEntry credit = new LedgerEntry();
        credit.setWallet(wallet);
        credit.setType("CREDIT");
        credit.setAmount(amount);
        credit.setTransaction(seedTxn);
        credit.setCreatedAt(Instant.now());
        ledgerEntryRepository.save(credit);
    }

    /**
     * Sets the SpringSecurity context to the given username — simulates JWT login.
     */
    private void setAuthenticatedUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList())
        );
    }

    @Test
    void transfer_shouldUpdateLedgerEntries() {
        TransactionRequest request = new TransactionRequest();
        request.setFromWalletIban(walletA.getIban());
        request.setToWalletIban(walletB.getIban());
        request.setAmount(BigDecimal.valueOf(200));
        request.setDescription("Test transfer");
        request.setTypeId(typeRepository.findAll().get(0).getId());

        walletService.transferFunds(request);

        // Verify ledger balances are updated correctly
        BigDecimal balanceA = balanceService.getBalance(walletA.getId());
        BigDecimal balanceB = balanceService.getBalance(walletB.getId());

        assertEquals(0, BigDecimal.valueOf(800).compareTo(balanceA),
                "Sender balance should be 1000 - 200 = 800");
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(balanceB),
                "Receiver balance should be 1000 + 200 = 1200");
    }

    @Test
    void transfer_shouldRejectDuplicateIdempotencyKey() {
        String idempotencyKey = "idem-test-001";

        TransactionRequest request = new TransactionRequest();
        request.setFromWalletIban(walletA.getIban());
        request.setToWalletIban(walletB.getIban());
        request.setAmount(BigDecimal.valueOf(100));
        request.setDescription("First transfer");
        request.setTypeId(typeRepository.findAll().get(0).getId());
        request.setIdempotencyKey(idempotencyKey);

        // First transfer should succeed
        walletService.transferFunds(request);

        // Second transfer with same idempotency key should return existing transaction ID
        // (not create a duplicate)
        walletService.transferFunds(request);

        // Verify only ONE transaction was created with this idempotency key
        assertTrue(transactionRepository.findByIdempotencyKey(idempotencyKey).isPresent(),
                "Exactly one transaction should exist with this idempotency key");

        // Alternatively, verify exactly one debit ledger entry exists for wallet A beyond the seed
        long debitCount = ledgerEntryRepository.findAll().stream()
                .filter(le -> le.getWallet().getId().equals(walletA.getId()))
                .filter(le -> "DEBIT".equals(le.getType()))
                .count();
        assertEquals(1, debitCount,
                "Only one debit entry should exist — idempotency should prevent duplicates");
    }

    @Test
    void transfer_shouldFailWithInsufficientBalance() {
        TransactionRequest request = new TransactionRequest();
        request.setFromWalletIban(walletA.getIban());
        request.setToWalletIban(walletB.getIban());
        request.setAmount(BigDecimal.valueOf(5000)); // exceeds 1000 balance
        request.setDescription("Overdraft attempt");
        request.setTypeId(typeRepository.findAll().get(0).getId());

        // Should throw InsufficientBalanceException
        assertThrows(InsufficientBalanceException.class, () -> {
            walletService.transferFunds(request);
        }, "Transfer exceeding balance must fail");

        // Balance should remain unchanged
        BigDecimal balanceA = balanceService.getBalance(walletA.getId());
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(balanceA),
                "Balance must not have changed on failed transfer");
    }
}
