package com.github.aryanaggarwal.service;

import com.github.aryanaggarwal.domain.event.TransactionCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class AuditLogListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTransactionCompletedEvent(TransactionCompletedEvent event) {
        log.info("ASYNC AUDIT TRIGGERED - Finalized securely. Logging transaction [{}] with idempotencyKey: {}", 
            event.transactionId(), event.idempotencyKey());
            
        // Simulating heavy I/O audit task to decoupled systems (e.g., Elasticsearch, Data Warehouse)
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
