package ma.adria.bank.transaction.service;

import ma.adria.bank.transaction.client.AccountGateway;
import ma.adria.bank.transaction.domain.AccountServiceUnavailableException;
import ma.adria.bank.transaction.domain.BusinessRuleException;
import ma.adria.bank.transaction.domain.ResourceNotFoundException;
import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static ma.adria.bank.transaction.TestAccounts.active;
import static ma.adria.bank.transaction.TestAccounts.blocked;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransactionServiceTest {

    @Autowired
    TransactionService service;

    @MockitoBean
    AccountGateway gateway;

    private static RegisterTransactionCommand command(UUID accountId, String amount, String currency) {
        return new RegisterTransactionCommand(accountId, new BigDecimal(amount), currency, "GROCERY", "FR");
    }

    @Test
    void registerChecksBalanceThenSavesThenUpdatesBalance() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 500));

        Transaction tx = service.register(command(id, "250", "eur"));

        assertThat(tx.getId()).isNotNull();
        assertThat(tx.getAmount()).isEqualByComparingTo("250.00");
        assertThat(tx.getCurrency()).isEqualTo("EUR");
        assertThat(tx.getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(tx.getCreatedAt()).isNotNull();
        assertThat(service.findByAccount(id)).hasSize(1);

        InOrder order = inOrder(gateway);
        order.verify(gateway).getAccount(id);
        order.verify(gateway).applyBalanceDelta(id, -250.0);
    }

    @Test
    void registerRejectsNonPositiveAmountWithoutCallingAccountService() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> service.register(command(id, "0", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(command(id, "-5", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.register(command(id, "0.001", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void registerEnforcesConfiguredCeiling() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 5000));

        assertThatThrownBy(() -> service.register(command(id, "1000.01", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("plafond");
        verifyNoInteractions(gateway);

        // Le plafond lui-même est autorisé
        assertThat(service.register(command(id, "1000", "EUR")).getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void registerFailsWhenAccountDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenThrow(new ResourceNotFoundException("Compte introuvable"));

        assertThatThrownBy(() -> service.register(command(id, "10", "EUR")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(service.findByAccount(id)).isEmpty();
        verify(gateway, never()).applyBalanceDelta(any(), anyDouble());
    }

    @Test
    void registerFailsWhenAccountIsBlocked() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(blocked(id, "EUR", 500));

        assertThatThrownBy(() -> service.register(command(id, "10", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("pas actif");
        assertThat(service.findByAccount(id)).isEmpty();
    }

    @Test
    void registerFailsWhenCurrencyDiffersFromAccount() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 500));

        assertThatThrownBy(() -> service.register(command(id, "10", "USD")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("devise");
        assertThat(service.findByAccount(id)).isEmpty();
    }

    @Test
    void registerFailsWhenBalanceIsInsufficient() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 50));

        assertThatThrownBy(() -> service.register(command(id, "50.01", "EUR")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Solde insuffisant");
        assertThat(service.findByAccount(id)).isEmpty();
    }

    @Test
    void registerIsRolledBackWhenBalanceUpdateFails() {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 500));
        when(gateway.applyBalanceDelta(id, -100.0))
                .thenThrow(new AccountServiceUnavailableException("indisponible", null));

        assertThatThrownBy(() -> service.register(command(id, "100", "EUR")))
                .isInstanceOf(AccountServiceUnavailableException.class);

        assertThat(service.findByAccount(id)).isEmpty();
    }

    @Test
    void transferCreatesLinkedDebitAndCreditAndMovesBalances() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 800));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 10));

        TransferResult result = service.transfer(new TransferCommand(from, to, new BigDecimal("300"), "EUR", "FR"));

        assertThat(result.transferId()).isNotNull();
        assertThat(result.debit().getAccountId()).isEqualTo(from);
        assertThat(result.debit().getType()).isEqualTo(TransactionType.DEBIT);
        assertThat(result.credit().getAccountId()).isEqualTo(to);
        assertThat(result.credit().getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(result.debit().getTransferId()).isEqualTo(result.transferId());
        assertThat(result.credit().getTransferId()).isEqualTo(result.transferId());
        assertThat(service.findByAccount(from)).hasSize(1);
        assertThat(service.findByAccount(to)).hasSize(1);
        verify(gateway).applyBalanceDelta(from, -300.0);
        verify(gateway).applyBalanceDelta(to, 300.0);
    }

    @Test
    void transferRejectsSameAccountAndInvalidTargets() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        assertThatThrownBy(() -> service.transfer(new TransferCommand(from, from, BigDecimal.TEN, "EUR", null)))
                .isInstanceOf(IllegalArgumentException.class);

        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 100));
        when(gateway.getAccount(to)).thenReturn(active(to, "MAD", 100));
        assertThatThrownBy(() -> service.transfer(new TransferCommand(from, to, BigDecimal.TEN, "EUR", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("devise");
        verify(gateway, never()).applyBalanceDelta(any(), anyDouble());
    }

    @Test
    void transferCompensatesDebitWhenCreditFails() {
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();
        when(gateway.getAccount(from)).thenReturn(active(from, "EUR", 500));
        when(gateway.getAccount(to)).thenReturn(active(to, "EUR", 0));
        doThrow(new AccountServiceUnavailableException("indisponible", null))
                .when(gateway).applyBalanceDelta(eq(to), anyDouble());

        assertThatThrownBy(() -> service.transfer(new TransferCommand(from, to, new BigDecimal("200"), "EUR", null)))
                .isInstanceOf(AccountServiceUnavailableException.class);

        InOrder order = inOrder(gateway);
        order.verify(gateway).applyBalanceDelta(from, -200.0);
        order.verify(gateway).applyBalanceDelta(to, 200.0);
        order.verify(gateway).applyBalanceDelta(from, 200.0);
        assertThat(service.findByAccount(from)).isEmpty();
        assertThat(service.findByAccount(to)).isEmpty();
    }

    @Test
    void getByIdThrowsWhenUnknown() {
        assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByAccountReturnsNewestFirst() throws InterruptedException {
        UUID id = UUID.randomUUID();
        when(gateway.getAccount(id)).thenReturn(active(id, "EUR", 1000));

        Transaction first = service.register(command(id, "10", "EUR"));
        Thread.sleep(10); // deux horodatages identiques rendraient l'ordre indéterminé
        Transaction second = service.register(command(id, "20", "EUR"));

        List<Transaction> result = service.findByAccount(id);
        assertThat(result).extracting(Transaction::getId).containsExactly(second.getId(), first.getId());
    }
}
