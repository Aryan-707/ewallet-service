package com.ewallet.service;

import com.ewallet.config.MessageSourceConfig;
import com.ewallet.dto.mapper.TransactionRequestMapper;
import com.ewallet.dto.mapper.TransactionResponseMapper;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.domain.entity.LedgerEntry;
import com.ewallet.domain.entity.Transaction;
import com.ewallet.domain.entity.Wallet;
import com.ewallet.repository.TransactionRepository;
import com.ewallet.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.ewallet.domain.event.TransactionCompletedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @InjectMocks
    private TransactionService transactionService;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TransactionRequestMapper transactionRequestMapper;

    @Mock
    private TransactionResponseMapper transactionResponseMapper;

    @Mock
    private MessageSourceConfig messageConfig;

    private Transaction testTransaction;
    private TransactionResponse testTransactionResponse;

    @BeforeEach
    void setUp() {
        testTransaction = new Transaction();
        testTransaction.setId(1L);
        testTransaction.setReferenceNumber(UUID.randomUUID());
        testTransaction.setAmount(BigDecimal.valueOf(100));

        testTransactionResponse = new TransactionResponse();
        testTransactionResponse.setId(1L);
        testTransactionResponse.setReferenceNumber(testTransaction.getReferenceNumber());
        testTransactionResponse.setAmount(BigDecimal.valueOf(100));
    }

    @Test
    void findById_shouldReturnTransactionResponse() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.of(testTransaction));
        when(transactionResponseMapper.toTransactionResponse(testTransaction)).thenReturn(testTransactionResponse);

        var result = transactionService.findById(1L);

        assertNotNull(result);
        assertEquals(testTransactionResponse, result);

        verify(transactionRepository).findById(1L);
        verify(transactionResponseMapper).toTransactionResponse(testTransaction);
    }

    @Test
    void findById_shouldThrowExceptionWhenTransactionNotFound() {
        when(transactionRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementFoundException.class, () -> transactionService.findById(1L));

        verify(transactionRepository).findById(1L);
    }

    @Test
    void findByReferenceNumber_shouldReturnTransactionResponse() {
        var referenceNumber = testTransaction.getReferenceNumber();

        when(transactionRepository.findByReferenceNumber(referenceNumber)).thenReturn(Optional.of(testTransaction));
        when(transactionResponseMapper.toTransactionResponse(testTransaction)).thenReturn(testTransactionResponse);

        var result = transactionService.findByReferenceNumber(referenceNumber);

        assertNotNull(result);
        assertEquals(testTransactionResponse, result);

        verify(transactionRepository).findByReferenceNumber(referenceNumber);
        verify(transactionResponseMapper).toTransactionResponse(testTransaction);
    }

    @Test
    void findByReferenceNumber_shouldThrowExceptionWhenTransactionNotFound() {
        var referenceNumber = UUID.randomUUID();

        when(transactionRepository.findByReferenceNumber(referenceNumber)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementFoundException.class, () -> transactionService.findByReferenceNumber(referenceNumber));

        verify(transactionRepository).findByReferenceNumber(referenceNumber);
    }

    @Test
    void findAllByUserId_shouldReturnPageOfTransactionResponses() {
        var userId = 1L;
        Pageable pageable = Pageable.unpaged();
        var transactions = new PageImpl<>(List.of(testTransaction, testTransaction));

        when(transactionRepository.findAllByUserId(userId, pageable)).thenReturn(transactions);
        when(transactionResponseMapper.toTransactionResponse(any(Transaction.class))).thenReturn(testTransactionResponse);

        var result = transactionService.findAllByUserId(userId, pageable);

        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals(testTransactionResponse, result.getContent().get(0));
        assertEquals(testTransactionResponse, result.getContent().get(1));

        verify(transactionRepository).findAllByUserId(userId, pageable);
        verify(transactionResponseMapper, times(2)).toTransactionResponse(any(Transaction.class));
    }

    @Test
    void findAllByUserId_shouldThrowExceptionWhenNoTransactionsFound() {
        var userId = 1L;
        Pageable pageable = Pageable.unpaged();

        when(transactionRepository.findAllByUserId(userId, pageable)).thenReturn(Page.empty());

        assertThrows(NoSuchElementFoundException.class, () -> transactionService.findAllByUserId(userId, pageable));

        verify(transactionRepository).findAllByUserId(userId, pageable);
    }

    @Test
    void findAll_shouldReturnPageOfTransactionResponses() {
        var pageable = Pageable.unpaged();
        var transactionPage = new PageImpl<>(List.of(testTransaction));

        when(transactionRepository.findAll(pageable)).thenReturn(transactionPage);
        when(transactionResponseMapper.toTransactionResponse(testTransaction)).thenReturn(testTransactionResponse);

        var result = transactionService.findAll(pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals(testTransactionResponse, result.getContent().get(0));

        verify(transactionRepository).findAll(pageable);
        verify(transactionResponseMapper).toTransactionResponse(testTransaction);
    }

    @Test
    void findAll_shouldThrowExceptionWhenNoTransactionsFound() {
        var pageable = Pageable.unpaged();

        when(transactionRepository.findAll(pageable)).thenReturn(Page.empty());

        assertThrows(NoSuchElementFoundException.class, () -> transactionService.findAll(pageable));

        verify(transactionRepository).findAll(pageable);
    }

    @Test
    void create_shouldCreateNewTransaction() {
        var request = new TransactionRequest();
        request.setAmount(BigDecimal.valueOf(100));

        var fromWallet = new Wallet();
        fromWallet.setIban("FROM123");

        var toWallet = new Wallet();
        toWallet.setIban("TO123");

        testTransaction.setFromWallet(fromWallet);
        testTransaction.setToWallet(toWallet);

        when(transactionRequestMapper.toTransaction(request)).thenReturn(testTransaction);
        when(transactionRepository.save(testTransaction)).thenReturn(testTransaction);

        var result = transactionService.create(request);

        assertNotNull(result);
        assertEquals(1L, result.id());

        verify(transactionRequestMapper).toTransaction(request);
        verify(transactionRepository, atLeastOnce()).save(testTransaction);
        verify(ledgerEntryRepository, times(2)).save(any(com.ewallet.domain.entity.LedgerEntry.class));
    }

    @Test
    void create_shouldRollbackAndSetFailedWhenCreditFails() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.TEN);

        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.TEN);
        transaction.setReferenceNumber(UUID.randomUUID());
        
        Wallet fromWallet = new Wallet(); fromWallet.setId(1L);
        Wallet toWallet = new Wallet(); toWallet.setId(2L);
        transaction.setFromWallet(fromWallet);
        transaction.setToWallet(toWallet);

        when(transactionRequestMapper.toTransaction(request)).thenReturn(transaction);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
            .thenReturn(new LedgerEntry()) // debit succeeds
            .thenThrow(new RuntimeException("Simulated transient failure 1"))
            .thenThrow(new RuntimeException("Simulated transient failure 2"))
            .thenThrow(new RuntimeException("Simulated permanent failure 3"));

        assertThrows(RuntimeException.class, () -> transactionService.create(request));

        verify(transactionRepository, times(2)).save(transaction);
        assertEquals(com.ewallet.domain.enums.Status.FAILED, transaction.getStatus());
    }

    @Test
    void create_shouldSucceedAfterTransientCreditFailure_RetrySuccessTest() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.TEN);

        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.TEN);
        transaction.setId(99L);
        transaction.setReferenceNumber(UUID.randomUUID());
        
        Wallet fromWallet = new Wallet(); fromWallet.setId(1L);
        Wallet toWallet = new Wallet(); toWallet.setId(2L);
        transaction.setFromWallet(fromWallet);
        transaction.setToWallet(toWallet);

        when(transactionRequestMapper.toTransaction(request)).thenReturn(transaction);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);
        
        // Mock transient failure: succeeds on 3rd attempt
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
            .thenReturn(new LedgerEntry()) // debit succeeds
            .thenThrow(new RuntimeException("Simulated transient failure 1"))
            .thenThrow(new RuntimeException("Simulated transient failure 2"))
            .thenReturn(new LedgerEntry()); // credit succeeds on 3rd retry

        CommandResponse response = transactionService.create(request);

        assertNotNull(response);
        assertEquals(99L, response.id());
        assertEquals(com.ewallet.domain.enums.Status.COMPLETED, transaction.getStatus());
        verify(ledgerEntryRepository, times(4)).save(any(LedgerEntry.class)); // 1 debit + 3 credit tries
    }
    
    @Test
    void create_shouldPublishEventWithoutBlocking_AsyncNonBlockingTest() {
        TransactionRequest request = new TransactionRequest();
        request.setAmount(BigDecimal.TEN);

        Transaction transaction = new Transaction();
        transaction.setAmount(BigDecimal.TEN);
        transaction.setId(100L);
        transaction.setReferenceNumber(UUID.randomUUID());
        transaction.setIdempotencyKey("idem-123");
        
        Wallet fromWallet = new Wallet(); fromWallet.setId(1L);
        transaction.setFromWallet(fromWallet);

        when(transactionRequestMapper.toTransaction(request)).thenReturn(transaction);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(transaction);

        long start = System.currentTimeMillis();
        CommandResponse response = transactionService.create(request);
        long duration = System.currentTimeMillis() - start;

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertTrue(duration < 500, "Should return immediately without waiting for async tasks");
        
        verify(applicationEventPublisher, times(1)).publishEvent(any(TransactionCompletedEvent.class));
    }
}
