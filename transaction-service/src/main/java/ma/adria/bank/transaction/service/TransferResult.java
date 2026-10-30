package ma.adria.bank.transaction.service;

import ma.adria.bank.transaction.domain.Transaction;

import java.util.UUID;

public record TransferResult(UUID transferId, Transaction debit, Transaction credit) {
}
