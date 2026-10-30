package ma.adria.bank.transaction.service;

import ma.adria.bank.transaction.client.AccountGateway;
import ma.adria.bank.transaction.config.BankTransactionProperties;
import ma.adria.bank.transaction.domain.AccountDto;
import ma.adria.bank.transaction.domain.AccountStatus;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.domain.TransactionType;
import ma.adria.bank.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** Point d'entrée unique des connecteurs REST, GraphQL et MCP. */
@Service
@Transactional
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final String TRANSFER_CATEGORY = "TRANSFER";
    private static final String UNKNOWN_COUNTRY = "N/A";

    private final TransactionRepository repository;
    private final AccountGateway accounts;
    private final BankTransactionProperties properties;
    private final Clock clock;

    public TransactionService(TransactionRepository repository, AccountGateway accounts,
                              BankTransactionProperties properties, Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Enregistre un débit : consultation du compte et contrôle des règles, enregistrement
     * de la transaction, puis mise à jour du solde. Si la mise à jour échoue, l'exception
     * annule l'enregistrement local.
     */
    public Transaction register(RegisterTransactionCommand command) {
        BigDecimal amount = checkAmount(command.amount());
        String currency = normalizeCurrency(command.currency());
        String category = requireText(command.merchantCategory(), "merchantCategory");
        String country = requireText(command.country(), "country");
        UUID accountId = require(command.accountId(), "accountId");

        AccountDto account = accounts.getAccount(accountId);
        checkUsable(account, accountId, currency);
        checkFunds(account, accountId, amount);

        Transaction transaction = repository.save(newTransaction(
                accountId, amount, currency, category, country, TransactionType.DEBIT, null));
        accounts.applyBalanceDelta(accountId, -amount.doubleValue());
        return transaction;
    }

    /**
     * Virement entre deux comptes de même devise : une transaction DEBIT sur la source et
     * une transaction CREDIT sur la destination, reliées par un transferId. Si le crédit
     * échoue après le débit, le débit est compensé.
     */
    public TransferResult transfer(TransferCommand command) {
        BigDecimal amount = checkAmount(command.amount());
        String currency = normalizeCurrency(command.currency());
        UUID from = require(command.fromAccountId(), "fromAccountId");
        UUID to = require(command.toAccountId(), "toAccountId");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Les comptes source et destination doivent être différents");
        }
        String country = command.country() == null || command.country().isBlank()
                ? UNKNOWN_COUNTRY : command.country().trim();

        AccountDto source = accounts.getAccount(from);
        AccountDto target = accounts.getAccount(to);
        checkUsable(source, from, currency);
        checkUsable(target, to, currency);
        checkFunds(source, from, amount);

        UUID transferId = UUID.randomUUID();
        Transaction debit = repository.save(newTransaction(
                from, amount, currency, TRANSFER_CATEGORY, country, TransactionType.DEBIT, transferId));
        Transaction credit = repository.save(newTransaction(
                to, amount, currency, TRANSFER_CATEGORY, country, TransactionType.CREDIT, transferId));

        accounts.applyBalanceDelta(from, -amount.doubleValue());
        try {
            accounts.applyBalanceDelta(to, amount.doubleValue());
        } catch (RuntimeException failure) {
            compensateDebit(from, amount, transferId);
            throw failure;
        }
        return new TransferResult(transferId, debit, credit);
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction introuvable : " + id));
    }

    @Transactional(readOnly = true)
    public List<Transaction> findByAccount(UUID accountId) {
        return repository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional(readOnly = true)
    public List<Transaction> findAll() {
        return repository.findAll();
    }

    private void compensateDebit(UUID accountId, BigDecimal amount, UUID transferId) {
        try {
            accounts.applyBalanceDelta(accountId, amount.doubleValue());
            log.warn("Virement {} : crédit impossible, débit du compte {} compensé", transferId, accountId);
        } catch (RuntimeException ex) {
            log.error("Virement {} : compensation du compte {} impossible, intervention manuelle requise",
                    transferId, accountId, ex);
        }
    }

    private Transaction newTransaction(UUID accountId, BigDecimal amount, String currency, String category,
                                       String country, TransactionType type, UUID transferId) {
        return new Transaction(accountId, amount, currency, category, country, clock.instant(), type, transferId);
    }

    private BigDecimal checkAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être strictement positif");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException("Le montant doit être strictement positif");
        }
        if (normalized.compareTo(properties.maxAmount()) > 0) {
            throw new BusinessRuleException(
                    "Le montant dépasse le plafond autorisé de " + properties.maxAmount().toPlainString());
        }
        return normalized;
    }

    private static void checkUsable(AccountDto account, UUID accountId, String currency) {
        if (account.status() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Le compte " + accountId + " n'est pas actif");
        }
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new BusinessRuleException("La devise " + currency + " ne correspond pas à celle du compte "
                    + accountId + " (" + account.currency() + ")");
        }
    }

    private static void checkFunds(AccountDto account, UUID accountId, BigDecimal amount) {
        if (BigDecimal.valueOf(account.balance()).compareTo(amount) < 0) {
            throw new BusinessRuleException("Solde insuffisant sur le compte " + accountId);
        }
    }

    private static String normalizeCurrency(String currency) {
        if (currency == null || !currency.matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency doit être un code de 3 lettres (ex : EUR)");
        }
        return currency.toUpperCase();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value.trim();
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value;
    }
}
