package ma.adria.bank.account.web.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import ma.adria.bank.account.domain.Account;
import ma.adria.bank.account.domain.AccountStatus;
import ma.adria.bank.account.service.AccountService;
import ma.adria.bank.account.service.CreateAccountCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountRestController {

    private final AccountService accountService;

    public AccountRestController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(new CreateAccountCommand(
                request.customerReference(), request.currency(), request.initialBalance()));
    }

    @GetMapping
    public List<Account> list(@RequestParam(required = false) String customerReference) {
        return customerReference == null
                ? accountService.findAll()
                : accountService.findByCustomerReference(customerReference);
    }

    @GetMapping("/{id}")
    public Account get(@PathVariable UUID id) {
        return accountService.getById(id);
    }

    @PatchMapping("/{id}/status")
    public Account changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return accountService.changeStatus(id, request.status());
    }

    /** Utilisé par transaction-service (OpenFeign) pour mettre à jour le solde. */
    @PatchMapping("/{id}/balance")
    public Account applyBalanceDelta(@PathVariable UUID id, @RequestBody BalanceDeltaRequest request) {
        return accountService.applyBalanceDelta(id, request.delta());
    }

    public record CreateAccountRequest(
            @NotBlank String customerReference,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "doit contenir 3 lettres") String currency,
            @PositiveOrZero double initialBalance) {
    }

    public record StatusRequest(@NotNull AccountStatus status) {
    }

    public record BalanceDeltaRequest(double delta) {
    }
}
