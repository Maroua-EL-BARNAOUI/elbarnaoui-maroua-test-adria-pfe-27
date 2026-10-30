package ma.adria.bank.transaction.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Plafond par transaction, fourni par le config-service (bank.transaction.max-amount).
 * Le démarrage échoue explicitement si la valeur est absente.
 */
@Validated
@ConfigurationProperties(prefix = "bank.transaction")
public record BankTransactionProperties(@NotNull @Positive BigDecimal maxAmount) {
}
