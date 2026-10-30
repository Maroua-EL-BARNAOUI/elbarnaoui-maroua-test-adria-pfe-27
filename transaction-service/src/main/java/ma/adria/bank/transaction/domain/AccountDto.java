package ma.adria.bank.transaction.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** Vue d'un compte telle que renvoyée par account-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountDto(UUID id, String customerReference, AccountStatus status, String currency, double balance) {
}
