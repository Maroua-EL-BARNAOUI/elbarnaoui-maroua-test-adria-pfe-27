package ma.adria.bank.account.config;

import ma.adria.bank.account.repository.AccountRepository;
import ma.adria.bank.account.service.AccountService;
import ma.adria.bank.account.service.CreateAccountCommand;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Jeu de données de démonstration (désactivable avec bank.demo-data=false). */
@Component
@ConditionalOnProperty(name = "bank.demo-data", havingValue = "true")
public class DemoDataLoader implements CommandLineRunner {

    private final AccountService accountService;
    private final AccountRepository repository;

    public DemoDataLoader(AccountService accountService, AccountRepository repository) {
        this.accountService = accountService;
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        accountService.create(new CreateAccountCommand("CUST-001", "EUR", 5000));
        accountService.create(new CreateAccountCommand("CUST-001", "MAD", 20000));
        accountService.create(new CreateAccountCommand("CUST-002", "EUR", 1200));
    }
}
