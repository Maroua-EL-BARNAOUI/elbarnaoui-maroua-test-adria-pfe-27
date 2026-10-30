package ma.adria.bank.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Modèle minimal du cahier des charges, complété par {@code type} (débit/crédit)
 * et {@code transferId} (lien entre les deux jambes d'un virement).
 */
@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_transactions_account", columnList = "accountId"))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private String merchantCategory;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    private UUID transferId;

    protected Transaction() {
    }

    public Transaction(UUID accountId, BigDecimal amount, String currency, String merchantCategory,
                       String country, Instant createdAt, TransactionType type, UUID transferId) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.merchantCategory = merchantCategory;
        this.country = country;
        this.createdAt = createdAt;
        this.type = type;
        this.transferId = transferId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getMerchantCategory() {
        return merchantCategory;
    }

    public String getCountry() {
        return country;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TransactionType getType() {
        return type;
    }

    public UUID getTransferId() {
        return transferId;
    }
}
