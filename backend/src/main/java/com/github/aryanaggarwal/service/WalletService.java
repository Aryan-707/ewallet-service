package com.github.aryanaggarwal.service;

import com.github.aryanaggarwal.config.MessageSourceConfig;
import com.github.aryanaggarwal.domain.entity.Wallet;
import com.github.aryanaggarwal.dto.mapper.WalletRequestMapper;
import com.github.aryanaggarwal.dto.mapper.WalletResponseMapper;
import com.github.aryanaggarwal.dto.mapper.WalletTransactionRequestMapper;
import com.github.aryanaggarwal.dto.request.TransactionRequest;
import com.github.aryanaggarwal.dto.request.WalletRequest;
import com.github.aryanaggarwal.dto.response.CommandResponse;
import com.github.aryanaggarwal.dto.response.WalletResponse;
import com.github.aryanaggarwal.dto.response.WalletStatsResponse;
import com.github.aryanaggarwal.exception.ElementAlreadyExistsException;
import com.github.aryanaggarwal.exception.InsufficientBalanceException;
import com.github.aryanaggarwal.exception.RateLimitExceededException;
import com.github.aryanaggarwal.exception.WalletNotFoundException;
import com.github.aryanaggarwal.exception.NoSuchElementFoundException;
import com.github.aryanaggarwal.repository.LedgerEntryRepository;
import com.github.aryanaggarwal.repository.TransactionRepository;
import com.github.aryanaggarwal.repository.UserRepository;
import com.github.aryanaggarwal.repository.WalletRepository;
import com.github.aryanaggarwal.validator.IbanValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.List;

import static com.github.aryanaggarwal.common.MessageKeys.*;

/**
 * Service used for Wallet related operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final MessageSourceConfig messageConfig;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;
    private final WalletRequestMapper walletRequestMapper;
    private final WalletResponseMapper walletResponseMapper;
    private final WalletTransactionRequestMapper walletTransactionRequestMapper;
    private final IbanValidator ibanValidator;
    private final RedisService redisService;
    private final BalanceService balanceService;

    /**
     * Prevents horizontal privilege escalation by verifying the
     * authenticated user owns the wallet being accessed.
     */
    private void verifyOwnership(Wallet wallet) {
        String currentUser = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName(); // username from JWT
        if (!wallet.getUser().getUsername().equals(currentUser)) {
            throw new AccessDeniedException(
                    "You do not own this wallet"
            ); // 403 — prevents accessing another user's wallet
        }
    }

    /**
     * Fetches a single wallet by the given id.
     *
     * @param id
     * @return WalletResponse
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WalletResponse findById(long id) {
        Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
        verifyOwnership(wallet); // ownership gate
        return walletResponseMapper.toWalletResponse(wallet);
    }

    /**
     * Fetches a single wallet by the given iban.
     *
     * @param iban
     * @return WalletResponse
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WalletResponse findByIban(String iban) {
        return walletRepository.findByIban(iban)
                .map(walletResponseMapper::toWalletResponse)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Fetches a single wallet by the given userId.
     *
     * @param userId
     * @return WalletResponse
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<WalletResponse> findByUserId(long userId) {
        return walletRepository.findByUserId(userId).stream()
                .map(walletResponseMapper::toWalletResponse)
                .toList();
    }

    /**
     * Fetches a single wallet reference (entity) by the given id.
     *
     * @param iban
     * @return Wallet
     */
    public Wallet getByIban(String iban) {
        return walletRepository.findByIban(iban)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Fetches a single wallet reference (entity) by the given iban with pessimistic lock.
     *
     * @param iban
     * @return Wallet
     */
    public Wallet getByIbanWithPessimisticWriteLock(String iban) {
        // needs the lock first or we get phantom reads
        return walletRepository.findByIbanWithPessimisticWriteLock(iban)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
    }

    /**
     * Returns total stats for the dashboard.
     */
    @Transactional(readOnly = true)
    public WalletStatsResponse getStats() {
        return new WalletStatsResponse(
                walletRepository.count(),
                transactionRepository.count(),
                userRepository.count()
        );
    }

    /**
     * Fetches all wallets based on the given paging and sorting parameters.
     *
     * @param pageable
     * @return List of WalletResponse
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<WalletResponse> findAll(Pageable pageable) {
        final Page<Wallet> wallets = walletRepository.findAll(pageable);
        if (wallets.isEmpty())
            throw new NoSuchElementFoundException(messageConfig.getMessage(ERROR_NO_RECORDS));
        return wallets.map(walletResponseMapper::toWalletResponse);
    }

    /**
     * Creates a new wallet using the given request parameters.
     *
     * @param request
     * @return id of the created wallet
     */
    @Transactional
    public CommandResponse create(WalletRequest request) {
        if (walletRepository.existsByIbanIgnoreCase(request.getIban()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_WALLET_IBAN_EXISTS));
        if (walletRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_WALLET_NAME_EXISTS));

        ibanValidator.isValid(request.getIban(), null);

        final Wallet wallet = walletRequestMapper.toWallet(request);
        walletRepository.save(wallet);
        log.info(messageConfig.getMessage(INFO_WALLET_CREATED, wallet.getIban(), wallet.getName(), java.math.BigDecimal.ZERO));

        // add this initial amount to the transactions
        transactionService.create(walletTransactionRequestMapper.toTransactionRequest(request));

        return CommandResponse.builder().id(wallet.getId()).build();
    }

    /**
     * Transfer funds between wallets.
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CommandResponse transferFunds(TransactionRequest request) {
        if (request.getIdempotencyKey() != null) {
            com.github.aryanaggarwal.domain.entity.Transaction existing = transactionService.findEntityByIdempotencyKey(request.getIdempotencyKey());
            if (existing != null) {
                return CommandResponse.builder().id(existing.getId()).build();
            }
        }
        final Wallet toWallet = getByIban(request.getToWalletIban());
        final Wallet fromWallet = getByIbanWithPessimisticWriteLock(request.getFromWalletIban());
        verifyOwnership(fromWallet); // only source wallet owner can initiate transfer

        if (redisService.isRateLimited(fromWallet.getUser().getId())) {
            throw new RateLimitExceededException(messageConfig.getMessage(ERROR_RATE_LIMIT_EXCEEDED));
        }

        // check if the balance of sender wallet has equal or higher to/than transfer amount
        java.math.BigDecimal fromBalance = balanceService.getBalance(fromWallet.getId());
        if (fromBalance.compareTo(request.getAmount()) < 0)
            throw new InsufficientBalanceException(messageConfig.getMessage(ERROR_INSUFFICIENT_FUNDS));

        // Note: Ledger entries are now created within transactionService.create(request);

        final CommandResponse response = transactionService.create(request);
        
        // Invalidate cache for BOTH affected wallets to ensure next getBalance is fresh
        redisService.evictBalanceCache(fromWallet.getId());
        redisService.evictBalanceCache(toWallet.getId());

        log.info(messageConfig.getMessage(INFO_WALLET_BALANCES_UPDATED, fromBalance.subtract(request.getAmount()), balanceService.getBalance(toWallet.getId())));
        return CommandResponse.builder().id(response.id()).build();
    }

    /**
     * Adds funds to the given wallet.
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional
    public CommandResponse addFunds(TransactionRequest request) {
        if (request.getIdempotencyKey() != null) {
            com.github.aryanaggarwal.domain.entity.Transaction existing = transactionService.findEntityByIdempotencyKey(request.getIdempotencyKey());
            if (existing != null) {
                return CommandResponse.builder().id(existing.getId()).build();
            }
        }
        final Wallet toWallet = getByIban(request.getToWalletIban());

        // Note: Ledger entries are now created within transactionService.create(request);

        final CommandResponse response = transactionService.create(request);
        
        // Invalidate cache for affected wallet
        redisService.evictBalanceCache(toWallet.getId());

        log.info(messageConfig.getMessage(INFO_WALLET_BALANCE_UPDATED, balanceService.getBalance(toWallet.getId())));
        return CommandResponse.builder().id(response.id()).build();
    }

    /**
     * Withdraw funds from the given wallet.
     *
     * @param request
     * @return id of the transaction
     */
    @Transactional
    public CommandResponse withdrawFunds(TransactionRequest request) {
        if (request.getIdempotencyKey() != null) {
            com.github.aryanaggarwal.domain.entity.Transaction existing = transactionService.findEntityByIdempotencyKey(request.getIdempotencyKey());
            if (existing != null) {
                return CommandResponse.builder().id(existing.getId()).build();
            }
        }
        final Wallet fromWallet = getByIbanWithPessimisticWriteLock(request.getFromWalletIban());
        verifyOwnership(fromWallet); // only wallet owner can withdraw

        // check if the balance of sender wallet has equal or higher to/than transfer amount
        java.math.BigDecimal fromBalance = balanceService.getBalance(fromWallet.getId());
        if (fromBalance.compareTo(request.getAmount()) < 0)
            throw new InsufficientBalanceException(messageConfig.getMessage(ERROR_INSUFFICIENT_FUNDS));

        // Note: Ledger entries are now created within transactionService.create(request);

        final CommandResponse response = transactionService.create(request);
        
        // Invalidate cache for affected wallet
        redisService.evictBalanceCache(fromWallet.getId());

        log.info(messageConfig.getMessage(INFO_WALLET_BALANCE_UPDATED, balanceService.getBalance(fromWallet.getId())));
        return CommandResponse.builder().id(response.id()).build();
    }

    /**
     * Updates wallet using the given request parameters.
     *
     * @param request
     * @return id of the updated wallet
     */
    public CommandResponse update(long id, WalletRequest request) {
        final Wallet foundWallet = walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
        verifyOwnership(foundWallet); // ownership gate

        // check if the iban is changed and new iban is already exists
        if (!request.getIban().equalsIgnoreCase(foundWallet.getIban()) &&
                walletRepository.existsByIbanIgnoreCase(request.getIban()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_WALLET_IBAN_EXISTS));

        // check if the name is changed and new name is already exists in user's wallets
        if (!request.getName().equalsIgnoreCase(foundWallet.getName()) &&
                walletRepository.existsByUserIdAndNameIgnoreCase(request.getUserId(), request.getName()))
            throw new ElementAlreadyExistsException(messageConfig.getMessage(ERROR_WALLET_NAME_EXISTS));

        ibanValidator.isValid(request.getIban(), null);

        final Wallet wallet = walletRequestMapper.toWallet(request);
        wallet.setId(foundWallet.getId());
        walletRepository.save(wallet);
        log.info(messageConfig.getMessage(INFO_WALLET_UPDATED, wallet.getIban(), wallet.getName(), ledgerEntryRepository.getBalance(wallet.getId())));
        return CommandResponse.builder().id(id).build();
    }

    /**
     * Deletes wallet by the given id.
     *
     * @param id
     */
    public void deleteById(long id) {
        final Wallet wallet = walletRepository.findById(id)
                .orElseThrow(() -> new WalletNotFoundException(messageConfig.getMessage(ERROR_WALLET_NOT_FOUND)));
        verifyOwnership(wallet); // ownership gate
        walletRepository.delete(wallet);
        log.info(messageConfig.getMessage(INFO_WALLET_DELETED, wallet.getIban(), wallet.getName(), ledgerEntryRepository.getBalance(wallet.getId())));
    }
}
