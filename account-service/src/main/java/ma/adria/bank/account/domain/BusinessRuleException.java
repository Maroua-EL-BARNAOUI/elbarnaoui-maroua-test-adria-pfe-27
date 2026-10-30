package ma.adria.bank.account.domain;

/** Violation d'une règle métier (compte bloqué, solde insuffisant...). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
