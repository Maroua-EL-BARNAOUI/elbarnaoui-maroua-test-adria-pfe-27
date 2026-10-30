package ma.adria.bank.transaction.domain;

/** account-service est injoignable ou le circuit est ouvert. */
public class AccountServiceUnavailableException extends RuntimeException {

    public AccountServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
