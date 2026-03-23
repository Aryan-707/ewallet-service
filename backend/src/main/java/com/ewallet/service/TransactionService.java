package com.ewallet.service;

import com.ewallet.config.MessageSourceConfig;
import com.ewallet.domain.entity.Transaction;
import com.ewallet.dto.mapper.TransactionRequestMapper;
import com.ewallet.dto.mapper.TransactionResponseMapper;
import com.ewallet.dto.request.TransactionRequest;
import com.ewallet.dto.response.CommandResponse;
import com.ewallet.dto.response.TransactionResponse;
import com.ewallet.exception.NoSuchElementFoundException;
import com.ewallet.repository.TransactionRepository;
import com.ewallet.repository.LedgerEntryRepository;
import com.ewallet.domain.entity.LedgerEntry;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import com.ewallet.domain.event.TransactionCompletedEvent;

import static com.ewallet.common.MessageKeys.*;

/**
 * Service used for Transaction related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final MessageSourceConfig messageConfig;
    private final TransactionRepository transactionRepository;
    private final TransactionRequestMapper transactionRequestMapper;
    private final TransactionResponseMapper transactionResponseMapper;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Fetches a single transaction by the given id.
     *
     * @param id
     * @return TransactionResponse
     */
    @Transactional(readOnly = true)
    public TransactionResponse findById(long id) {
        return transactionRepository.findById(id)
                .map(transactionResponseMapper::toTransactionResponse)
                .orElseThrow(() -> new NoSuchElementFoundException(messageConfig.getMessage(ERROR_TRANSACTION_NOT_FOUND)));
    }

    /**
     * Fetches a single transaction by the given referenceNumber.
     *
     * @param referenceNumber
     * @return TransactionResponse
     */
    @Transactional(readOnly = true)
    public TransactionResponse findByReferenceNumber(UUID referenceNumber) {
        return transactionRepository.findByReferenceNumber(referenceNumber)
                .map(transactionResponseMapper::toTransactionResponse)
                .orElseThrow(() -> new NoSuchElementFoundException(messageConfig.getMessage(ERROR_TRANSACTION_NOT_FOUND)));
    }

    public Transaction findEntityByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return null;
        return transactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
    }

    /**
     * Fetches all transaction by the given userId.
     *
     * @param userId
     * @return List of TransactionResponse
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TransactionResponse> findAllByUserId(long userId, org.springframework.data.domain.Pageable pageable) {
        final org.springframework.data.domain.Page<Transaction> transactions = transactionRepository.findAllByUserId(userId, pageable);
        if (transactions.isEmpty())
            throw new NoSuchElementFoundException(messageConfig.getMessage(ERROR_TRANSACTION_NOT_FOUND));
        return transactions.map(transactionResponseMapper::toTransactionResponse);
    }

    /**
     * Fetches all transactions based on the given paging and sorting parameters.
     *
     * @param pageable
     * @return List of TransactionResponse
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAll(Pageable pageable) {
        final Page<Transaction> transactions = transactionRepository.findAll(pageable);
        if (transactions.isEmpty())
            throw new NoSuchElementFoundException(messageConfig.getMessage(ERROR_NO_RECORDS));

        return transactions.map(transactionResponseMapper::toTransactionResponse);
    }

    /**
     * Creates a new transaction using the given request parameters.
     *
     * @param request
     * @return id of the created transaction
     */
    @Transactional
    public CommandResponse create(TransactionRequest request) {
        Transaction transaction = transactionRequestMapper.toTransaction(request);
        transaction = transactionRepository.save(transaction);
        
        org.slf4j.MDC.put("transactionId", transaction.getReferenceNumber().toString());
        try {
            // DEBIT from Source
            if (transaction.getFromWallet() != null) {
                LedgerEntry debit = new LedgerEntry();
                debit.setWallet(transaction.getFromWallet());
                debit.setType("DEBIT");
                debit.setAmount(transaction.getAmount());
                debit.setTransaction(transaction);
                debit.setCreatedAt(Instant.now());
                ledgerEntryRepository.save(debit);
            }

            // CREDIT to Target
            if (transaction.getToWallet() != null) {
                int maxRetries = 3;
                int attempt = 0;
                while (attempt < maxRetries) {
                    try {
                        attempt++;
                        LedgerEntry credit = new LedgerEntry();
                        credit.setWallet(transaction.getToWallet());
                        credit.setType("CREDIT");
                        credit.setAmount(transaction.getAmount());
                        credit.setTransaction(transaction);
                        credit.setCreatedAt(Instant.now());
                        ledgerEntryRepository.save(credit);
                        break; // Success
                    } catch (Exception e) {
                        if (attempt == maxRetries) {
                            log.error("CREDIT max retries exceeded for transaction: {}", transaction.getId());
                            throw e; // permanently fail
                        }
                        log.warn("Transient failure in CREDIT step, retrying... attempt {}", attempt);
                    }
                }
            }
            
            transaction.setStatus(com.ewallet.domain.enums.Status.COMPLETED);
            transactionRepository.save(transaction);
            
            String action = transaction.getFromWallet() != null && transaction.getToWallet() != null ? "TRANSFER" : (transaction.getFromWallet() == null ? "ADD_FUNDS" : "WITHDRAW_FUNDS");
            String fromIban = transaction.getFromWallet() != null ? transaction.getFromWallet().getIban() : "null";
            String toIban = transaction.getToWallet() != null ? transaction.getToWallet().getIban() : "null";
            String idemKey = transaction.getIdempotencyKey() != null ? transaction.getIdempotencyKey() : "null";
            
            log.info("{\"action\":\"{}\", \"amount\":{}, \"fromIban\":\"{}\", \"toIban\":\"{}\", \"idempotencyKey\":\"{}\"}",
                action, transaction.getAmount(), fromIban, toIban, idemKey);
                
            applicationEventPublisher.publishEvent(new TransactionCompletedEvent(transaction.getId(), transaction.getIdempotencyKey()));

                
        } catch (Exception ex) {
            transaction.setStatus(com.ewallet.domain.enums.Status.FAILED);
            transactionRepository.save(transaction);
            log.error("{\"action\":\"TRANSACTION_FAILED\", \"error\":\"{}\"}", ex.getMessage());
            throw ex;
        } finally {
            org.slf4j.MDC.remove("transactionId");
        }

        return CommandResponse.builder().id(transaction.getId()).build();
    }
}
