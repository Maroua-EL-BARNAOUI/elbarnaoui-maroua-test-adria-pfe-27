package ma.adria.bank.account.service;

import ma.adria.bank.account.domain.Account;
import ma.adria.bank.account.domain.AccountNotFoundException;
import ma.adria.bank.account.domain.AccountStatus;
import ma.adria.bank.account.domain.BusinessRuleException;
import ma.adria.bank.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AccountService.class)
class AccountServiceTest {

    @Autowired
    AccountService service;

    @Autowired
    AccountRepository repository;

    @Test
    void createStoresActiveAccountWithNormalizedCurrency() {
        Account account = service.create(new CreateAccountCommand(" CUST-1 ", "eur", 100));

        assertThat(account.getId()).isNotNull();
        assertThat(account.getCustomerReference()).isEqualTo("CUST-1");
        assertThat(account.getCurrency()).isEqualTo("EUR");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getBalance()).isEqualTo(100);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void createRejectsInvalidInput() {
        assertThatThrownBy(() -> service.create(new CreateAccountCommand("", "EUR", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CreateAccountCommand("C", "EURO", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new CreateAccountCommand("C", "EUR", -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getByIdThrowsWhenAccountDoesNotExist() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void findByCustomerReferenceFiltersAccounts() {
        service.create(new CreateAccountCommand("A", "EUR", 1));
        service.create(new CreateAccountCommand("A", "MAD", 1));
        service.create(new CreateAccountCommand("B", "EUR", 1));

        assertThat(service.findByCustomerReference("A")).hasSize(2);
        assertThat(service.findAll()).hasSize(3);
    }

    @Test
    void applyBalanceDeltaCreditsAndDebits() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 100));

        assertThat(service.applyBalanceDelta(account.getId(), 50.25).getBalance()).isEqualTo(150.25);
        assertThat(service.applyBalanceDelta(account.getId(), -150.25).getBalance()).isEqualTo(0);
    }

    @Test
    void applyBalanceDeltaAvoidsFloatingPointDrift() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 0));

        service.applyBalanceDelta(account.getId(), 0.1);
        Account result = service.applyBalanceDelta(account.getId(), 0.2);

        assertThat(result.getBalance()).isEqualTo(0.3);
    }

    @Test
    void applyBalanceDeltaRejectsOverdraft() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 10));

        assertThatThrownBy(() -> service.applyBalanceDelta(account.getId(), -10.01))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solde insuffisant");
        assertThat(service.getById(account.getId()).getBalance()).isEqualTo(10);
    }

    @Test
    void applyBalanceDeltaRejectsBlockedAccount() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 10));
        service.changeStatus(account.getId(), AccountStatus.BLOCKED);

        assertThatThrownBy(() -> service.applyBalanceDelta(account.getId(), 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("bloqué");
    }

    @Test
    void applyBalanceDeltaRejectsNonFiniteValue() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 10));

        assertThatThrownBy(() -> service.applyBalanceDelta(account.getId(), Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changeStatusUpdatesAccount() {
        Account account = service.create(new CreateAccountCommand("A", "EUR", 10));

        assertThat(service.changeStatus(account.getId(), AccountStatus.BLOCKED).getStatus())
                .isEqualTo(AccountStatus.BLOCKED);
        assertThat(service.getById(account.getId()).getStatus()).isEqualTo(AccountStatus.BLOCKED);
    }
}
