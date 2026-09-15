package com.fiap.bank.atm.application.dto;

import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionDTO(
        UUID id,
        LocalDateTime timestamp,
        TransactionType type,
        BigDecimal amount,
        String description) {

    public static TransactionDTO from(Transaction transaction) {
        return new TransactionDTO(
                transaction.getId(),
                transaction.getTimestamp(),
                transaction.getType(),
                transaction.getAmount().getAmount(),
                transaction.getDescription());
    }
}
