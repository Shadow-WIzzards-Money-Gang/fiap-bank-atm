package com.fiap.bank.atm.application.dto;

import com.fiap.bank.atm.domain.model.Account;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountInfoDTO(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        BigDecimal dailyWithdrawalLimit,
        BigDecimal totalWithdrawnToday,
        boolean blocked) {

    public static AccountInfoDTO from(Account account) {
        return new AccountInfoDTO(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance().getAmount(),
                account.getDailyWithdrawalLimit().getAmount(),
                account.getTotalWithdrawnToday().getAmount(),
                account.isBlocked());
    }
}
