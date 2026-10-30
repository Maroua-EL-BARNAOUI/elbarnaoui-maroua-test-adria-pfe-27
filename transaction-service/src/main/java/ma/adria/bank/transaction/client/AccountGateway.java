package ma.adria.bank.transaction.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import ma.adria.bank.transaction.domain.AccountDto;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Point d'accès tolérant aux pannes vers account-service.
 * <ul>
 *   <li>Circuit breaker sur tous les appels : un account-service défaillant est isolé.</li>
 *   <li>Retry uniquement sur la lecture (idempotente) : rejouer une mise à jour de solde
 *       pourrait appliquer deux fois le même débit.</li>
 *   <li>Fallback : on ne fabrique jamais de solde ; l'appelant reçoit une erreur 503 explicite.</li>
 * </ul>
 */
@Component
public class AccountGateway {

    private static final Logger log = LoggerFactory.getLogger(AccountGateway.class);
    private static final String INSTANCE = "account-service";

    private final AccountClient client;

    public AccountGateway(AccountClient client) {
        this.client = client;
    }

    // Le fallback est porté par @Retry (couche externe) : le retry voit les vraies exceptions et
    // n'insiste pas quand le circuit est ouvert (CallNotPermittedException ignorée).
    @Retry(name = INSTANCE, fallbackMethod = "getAccountFallback")
    @CircuitBreaker(name = INSTANCE)
    public AccountDto getAccount(UUID accountId) {
        return client.getAccount(accountId);
    }

    @CircuitBreaker(name = INSTANCE, fallbackMethod = "applyBalanceDeltaFallback")
    public AccountDto applyBalanceDelta(UUID accountId, double delta) {
        return client.applyBalanceDelta(accountId, new AccountClient.BalanceDeltaRequest(delta));
    }

    @SuppressWarnings("unused")
    private AccountDto getAccountFallback(UUID accountId, Throwable cause) {
        throw translate("consultation du compte " + accountId, cause);
    }

    @SuppressWarnings("unused")
    private AccountDto applyBalanceDeltaFallback(UUID accountId, double delta, Throwable cause) {
        throw translate("mise à jour du solde du compte " + accountId, cause);
    }

    /** Les erreurs métier traversent le fallback ; seules les vraies pannes deviennent un 503. */
    private static RuntimeException translate(String operation, Throwable cause) {
        if (cause instanceof ResourceNotFoundException || cause instanceof BusinessRuleException) {
            return (RuntimeException) cause;
        }
        log.warn("account-service indisponible pour la {} : {}", operation, cause.toString());
        return new AccountServiceUnavailableException(
                "account-service est indisponible (" + operation + "), veuillez réessayer plus tard", cause);
    }
}
