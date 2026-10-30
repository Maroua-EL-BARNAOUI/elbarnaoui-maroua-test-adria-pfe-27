package ma.adria.bank.account.service;

import ma.adria.bank.account.domain.Account;
import ma.adria.bank.account.domain.AccountNotFoundException;
import ma.adria.bank.account.domain.AccountStatus;
import ma.adria.bank.account.domain.BusinessRuleException;
import ma.adria.bank.account.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Point d'entrée unique des connecteurs REST, GraphQL et MCP. */
@Service
@Transactional
public class AccountService {

    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Account create(CreateAccountCommand command) {
        if (command.customerReference() == null || command.customerReference().isBlank()) {
            throw new IllegalArgumentException("customerReference est obligatoire");
        }
        if (command.currency() == null || !command.currency().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException("currency doit être un code de 3 lettres (ex : EUR)");
        }
        if (!Double.isFinite(command.initialBalance()) || command.initialBalance() < 0) {
            throw new IllegalArgumentException("Le solde initial doit être positif ou nul");
        }
        return repository.save(new Account(
                command.customerReference().trim(),
                command.currency().toUpperCase(),
                round(command.initialBalance())));
    }

    @Transactional(readOnly = true)
    public Account getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Account> findByCustomerReference(String customerReference) {
        return repository.findByCustomerReference(customerReference);
    }

    public Account changeStatus(UUID id, AccountStatus status) {
        Account account = getById(id);
        account.setStatus(status);
        return account;
    }

    /**
     * Applique une variation de solde (négative pour un débit) sur un compte actif.
     * Le solde ne peut pas devenir négatif.
     */
    public Account applyBalanceDelta(UUID id, double delta) {
        if (!Double.isFinite(delta)) {
            throw new IllegalArgumentException("La variation de solde est invalide");
        }
        Account account = repository.findByIdForUpdate(id).orElseThrow(() -> new AccountNotFoundException(id));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Le compte " + id + " est bloqué");
        }
        double newBalance = round(account.getBalance() + delta);
        if (newBalance < 0) {
            throw new BusinessRuleException("Solde insuffisant sur le compte " + id);
        }
        account.setBalance(newBalance);
        return account;
    }

    /** Arrondi à 2 décimales pour limiter la dérive des calculs en double. */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
