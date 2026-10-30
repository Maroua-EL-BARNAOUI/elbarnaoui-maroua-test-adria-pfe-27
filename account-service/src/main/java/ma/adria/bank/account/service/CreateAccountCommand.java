package ma.adria.bank.account.service;

public record CreateAccountCommand(String customerReference, String currency, double initialBalance) {
}
