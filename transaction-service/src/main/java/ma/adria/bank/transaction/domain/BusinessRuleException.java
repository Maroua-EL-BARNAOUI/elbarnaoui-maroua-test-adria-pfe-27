package ma.adria.bank.transaction.domain;

/** Violation d'une règle métier (plafond, compte inactif, devise, solde insuffisant...). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
