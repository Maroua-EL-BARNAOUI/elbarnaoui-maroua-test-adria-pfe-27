package ma.adria.bank.transaction.web.graphql;

import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.service.RegisterTransactionCommand;
import ma.adria.bank.transaction.service.TransactionService;
import ma.adria.bank.transaction.service.TransferCommand;
import ma.adria.bank.transaction.service.TransferResult;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
public class TransactionGraphqlController {

    private final TransactionService transactionService;

    public TransactionGraphqlController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @QueryMapping
    public Transaction transactionById(@Argument UUID id) {
        try {
            return transactionService.getById(id);
        } catch (ResourceNotFoundException ex) {
            return null;
        }
    }

    @QueryMapping
    public List<Transaction> transactionsByAccount(@Argument UUID accountId) {
        return transactionService.findByAccount(accountId);
    }

    @MutationMapping
    public Transaction registerTransaction(@Argument RegisterTransactionInput input) {
        return transactionService.register(new RegisterTransactionCommand(
                input.accountId(), input.amount(), input.currency(),
                input.merchantCategory(), input.country()));
    }

    @MutationMapping
    public TransferResult transfer(@Argument TransferInput input) {
        return transactionService.transfer(new TransferCommand(
                input.fromAccountId(), input.toAccountId(), input.amount(),
                input.currency(), input.country()));
    }

    public record RegisterTransactionInput(UUID accountId, BigDecimal amount, String currency,
                                           String merchantCategory, String country) {
    }

    public record TransferInput(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency,
                                String country) {
    }
}
