package ma.adria.bank.account.mcp;

import ma.adria.bank.account.domain.Account;
import ma.adria.bank.account.domain.AccountStatus;
import ma.adria.bank.account.service.AccountService;
import ma.adria.bank.account.service.CreateAccountCommand;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/** Outils MCP consommés par l'assistant conversationnel. */
@Component
public class AccountMcpTools {

    private final AccountService accountService;

    public AccountMcpTools(AccountService accountService) {
        this.accountService = accountService;
    }

    @Tool(name = "list_accounts", description = "Liste tous les comptes bancaires avec leur statut, devise et solde")
    public List<Account> listAccounts() {
        return accountService.findAll();
    }

    @Tool(name = "get_account", description = "Retourne un compte bancaire à partir de son identifiant UUID")
    public Account getAccount(@ToolParam(description = "Identifiant UUID du compte") String accountId) {
        return accountService.getById(UUID.fromString(accountId));
    }

    @Tool(name = "get_accounts_by_customer", description = "Liste les comptes d'un client à partir de sa référence")
    public List<Account> getAccountsByCustomer(
            @ToolParam(description = "Référence du client, par exemple CUST-001") String customerReference) {
        return accountService.findByCustomerReference(customerReference);
    }

    @Tool(name = "create_account", description = "Crée un nouveau compte bancaire actif")
    public Account createAccount(
            @ToolParam(description = "Référence du client") String customerReference,
            @ToolParam(description = "Code devise sur 3 lettres, par exemple EUR") String currency,
            @ToolParam(description = "Solde initial, positif ou nul") double initialBalance) {
        return accountService.create(new CreateAccountCommand(customerReference, currency, initialBalance));
    }

    @Tool(name = "change_account_status", description = "Active ou bloque un compte bancaire")
    public Account changeAccountStatus(
            @ToolParam(description = "Identifiant UUID du compte") String accountId,
            @ToolParam(description = "Nouveau statut : ACTIVE ou BLOCKED") String status) {
        return accountService.changeStatus(UUID.fromString(accountId), AccountStatus.valueOf(status));
    }
}
