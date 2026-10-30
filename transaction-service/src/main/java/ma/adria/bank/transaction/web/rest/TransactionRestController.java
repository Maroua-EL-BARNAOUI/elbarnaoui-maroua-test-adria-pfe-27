package ma.adria.bank.transaction.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.service.RegisterTransactionCommand;
import ma.adria.bank.transaction.service.TransactionService;
import ma.adria.bank.transaction.service.TransferCommand;
import ma.adria.bank.transaction.service.TransferResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TransactionRestController {

    private final TransactionService transactionService;

    public TransactionRestController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction register(@Valid @RequestBody RegisterTransactionRequest request) {
        return transactionService.register(new RegisterTransactionCommand(
                request.accountId(), request.amount(), request.currency(),
                request.merchantCategory(), request.country()));
    }

    @GetMapping("/transactions")
    public List<Transaction> list(@RequestParam(required = false) UUID accountId) {
        return accountId == null ? transactionService.findAll() : transactionService.findByAccount(accountId);
    }

    @GetMapping("/transactions/{id}")
    public Transaction get(@PathVariable UUID id) {
        return transactionService.getById(id);
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResult transfer(@Valid @RequestBody TransferRequest request) {
        return transactionService.transfer(new TransferCommand(
                request.fromAccountId(), request.toAccountId(), request.amount(),
                request.currency(), request.country()));
    }

    public record RegisterTransactionRequest(
            @NotNull UUID accountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "doit contenir 3 lettres") String currency,
            @NotBlank String merchantCategory,
            @NotBlank String country) {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @Positive BigDecimal amount,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "doit contenir 3 lettres") String currency,
            String country) {
    }
}
