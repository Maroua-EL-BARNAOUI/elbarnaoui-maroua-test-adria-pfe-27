package ma.adria.bank.transaction;

import ma.adria.bank.transaction.domain.AccountDto;
import ma.adria.bank.transaction.domain.AccountStatus;

import java.util.UUID;

public final class TestAccounts {

    private TestAccounts() {
    }

    public static AccountDto active(UUID id, String currency, double balance) {
        return new AccountDto(id, "CUST-TEST", AccountStatus.ACTIVE, currency, balance);
    }

    public static AccountDto blocked(UUID id, String currency, double balance) {
        return new AccountDto(id, "CUST-TEST", AccountStatus.BLOCKED, currency, balance);
    }
}
