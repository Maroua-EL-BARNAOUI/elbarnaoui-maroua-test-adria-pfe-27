package ma.adria.bank.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Plafond par transaction, fourni par le config-service (bank.transaction.max-amount). */
@ConfigurationProperties(prefix = "bank.transaction")
public record BankTransactionProperties(BigDecimal maxAmount) {
}
