package ma.adria.bank.transaction.mcp;

import ma.adria.bank.transaction.domain.Transaction;
import ma.adria.bank.transaction.service.RegisterTransactionCommand;
import ma.adria.bank.transaction.service.TransactionService;
import ma.adria.bank.transaction.service.TransferCommand;
import ma.adria.bank.transaction.service.TransferResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Outils MCP consommés par l'assistant conversationnel. */
@Component
public class TransactionMcpTools {

    private final TransactionService transactionService;

    public TransactionMcpTools(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Tool(name = "register_transaction",
            description = "Enregistre un paiement (débit) sur un compte. Le compte doit être actif, la devise "
                    + "identique à celle du compte et le montant inférieur au plafond autorisé")
    public Transaction registerTransaction(
            @ToolParam(description = "Identifiant UUID du compte") String accountId,
            @ToolParam(description = "Montant strictement positif") double amount,
            @ToolParam(description = "Code devise sur 3 lettres, identique à celle du compte") String currency,
            @ToolParam(description = "Catégorie du commerçant, par exemple GROCERY") String merchantCategory,
            @ToolParam(description = "Pays de la transaction, par exemple FR") String country) {
        return transactionService.register(new RegisterTransactionCommand(
                UUID.fromString(accountId), BigDecimal.valueOf(amount), currency, merchantCategory, country));
    }

    @Tool(name = "transfer_funds",
            description = "Effectue un virement d'un compte source vers un compte destination de même devise")
    public TransferResult transferFunds(
            @ToolParam(description = "Identifiant UUID du compte source") String fromAccountId,
            @ToolParam(description = "Identifiant UUID du compte destination") String toAccountId,
            @ToolParam(description = "Montant strictement positif") double amount,
            @ToolParam(description = "Code devise sur 3 lettres commun aux deux comptes") String currency) {
        return transactionService.transfer(new TransferCommand(
                UUID.fromString(fromAccountId), UUID.fromString(toAccountId),
                BigDecimal.valueOf(amount), currency, null));
    }

    @Tool(name = "get_transaction", description = "Retourne une transaction à partir de son identifiant UUID")
    public Transaction getTransaction(@ToolParam(description = "Identifiant UUID de la transaction") String transactionId) {
        return transactionService.getById(UUID.fromString(transactionId));
    }

    @Tool(name = "list_transactions_by_account",
            description = "Liste les transactions d'un compte, de la plus récente à la plus ancienne")
    public List<Transaction> listTransactionsByAccount(
            @ToolParam(description = "Identifiant UUID du compte") String accountId) {
        return transactionService.findByAccount(UUID.fromString(accountId));
    }
}
