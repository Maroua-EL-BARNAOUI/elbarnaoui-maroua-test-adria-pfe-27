package ma.adria.bank.account.web.graphql;

import ma.adria.bank.account.domain.Account;
import ma.adria.bank.account.domain.AccountNotFoundException;
import ma.adria.bank.account.domain.AccountStatus;
import ma.adria.bank.account.service.AccountService;
import ma.adria.bank.account.service.CreateAccountCommand;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
public class AccountGraphqlController {

    private final AccountService accountService;

    public AccountGraphqlController(AccountService accountService) {
        this.accountService = accountService;
    }

    @QueryMapping
    public List<Account> accounts() {
        return accountService.findAll();
    }

    @QueryMapping
    public Account accountById(@Argument UUID id) {
        try {
            return accountService.getById(id);
        } catch (AccountNotFoundException ex) {
            return null;
        }
    }

    @QueryMapping
    public List<Account> accountsByCustomer(@Argument String customerReference) {
        return accountService.findByCustomerReference(customerReference);
    }

    @MutationMapping
    public Account createAccount(@Argument CreateAccountInput input) {
        return accountService.create(new CreateAccountCommand(
                input.customerReference(), input.currency(), input.initialBalance()));
    }

    @MutationMapping
    public Account changeAccountStatus(@Argument UUID id, @Argument AccountStatus status) {
        return accountService.changeStatus(id, status);
    }

    public record CreateAccountInput(String customerReference, String currency, double initialBalance) {
    }
}
