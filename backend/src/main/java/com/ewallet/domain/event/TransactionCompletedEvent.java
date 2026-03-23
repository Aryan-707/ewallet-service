package com.ewallet.domain.event;

public record TransactionCompletedEvent(Long transactionId, String idempotencyKey) {
}
