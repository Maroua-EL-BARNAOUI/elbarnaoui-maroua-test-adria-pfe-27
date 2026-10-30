package ma.adria.bank.transaction.service;

import java.math.BigDecimal;
import java.util.UUID;

public record RegisterTransactionCommand(UUID accountId, BigDecimal amount, String currency,
                                         String merchantCategory, String country) {
}
