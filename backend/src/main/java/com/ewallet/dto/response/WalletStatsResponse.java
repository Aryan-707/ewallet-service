package com.ewallet.dto.response;

/**
 * Aggregated statistics for the dashboard overview cards.
 */
public record WalletStatsResponse(
        long totalWallets,
        long totalTransactions,
        long totalUsers
) {}
