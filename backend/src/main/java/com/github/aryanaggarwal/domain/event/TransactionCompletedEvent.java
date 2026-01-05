package com.github.aryanaggarwal.domain.event;

public record TransactionCompletedEvent(Long transactionId, String idempotencyKey) {
}
