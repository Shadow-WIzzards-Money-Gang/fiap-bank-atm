package com.fiap.bank.atm.infrastructure.persistence;

import com.fiap.bank.atm.domain.model.Account;
import com.fiap.bank.atm.domain.model.Money;
import com.fiap.bank.atm.domain.model.Transaction;
import com.fiap.bank.atm.domain.model.TransactionType;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação JDBC de {@link AccountRepository}, persistindo contas e transações em SQLite
 * exclusivamente via {@link PreparedStatement} e {@link ResultSet}.
 */
public class AccountRepositoryJdbcImpl implements AccountRepository {
    private final SqliteConnectionFactory connectionFactory;

    public AccountRepositoryJdbcImpl(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
        seedDataIfEmpty();
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT id, account_number, pin, balance, daily_withdrawal_limit, "
                + "total_withdrawn_today, blocked, failed_attempts FROM tb_account WHERE account_number = ?";

        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Account account = mapAccount(rs);
                    loadTransactions(connection, account);
                    return Optional.of(account);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar conta pelo número.", e);
        }
    }

    @Override
    public Optional<Account> findById(UUID id) {
        String sql = "SELECT id, account_number, pin, balance, daily_withdrawal_limit, "
                + "total_withdrawn_today, blocked, failed_attempts FROM tb_account WHERE id = ?";

        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, id.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Account account = mapAccount(rs);
                    loadTransactions(connection, account);
                    return Optional.of(account);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar conta pelo id.", e);
        }
    }

    @Override
    public List<Account> findAll() {
        String sql = "SELECT id, account_number, pin, balance, daily_withdrawal_limit, "
                + "total_withdrawn_today, blocked, failed_attempts FROM tb_account";

        List<Account> accounts = new ArrayList<>();
        try (Connection connection = connectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Account account = mapAccount(rs);
                loadTransactions(connection, account);
                accounts.add(account);
            }
            return accounts;
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar todas as contas.", e);
        }
    }

    @Override
    public void save(Account account) {
        String upsertAccount = """
                INSERT INTO tb_account (id, account_number, pin, balance, daily_withdrawal_limit,
                    total_withdrawn_today, blocked, failed_attempts)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    balance = excluded.balance,
                    total_withdrawn_today = excluded.total_withdrawn_today,
                    blocked = excluded.blocked,
                    failed_attempts = excluded.failed_attempts
                """;

        String insertTransaction = """
                INSERT OR IGNORE INTO tb_transaction (id, account_id, type, amount, description, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = connectionFactory.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement(upsertAccount)) {
                ps.setString(1, account.getId().toString());
                ps.setString(2, account.getAccountNumber());
                ps.setString(3, account.getPin());
                ps.setBigDecimal(4, account.getBalance().getAmount());
                ps.setBigDecimal(5, account.getDailyWithdrawalLimit().getAmount());
                ps.setBigDecimal(6, account.getTotalWithdrawnToday().getAmount());
                ps.setInt(7, account.isBlocked() ? 1 : 0);
                ps.setInt(8, account.getFailedAttempts());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(insertTransaction)) {
                for (Transaction transaction : account.getTransactions()) {
                    ps.setString(1, transaction.getId().toString());
                    ps.setString(2, account.getId().toString());
                    ps.setString(3, transaction.getType().name());
                    ps.setBigDecimal(4, transaction.getAmount().getAmount());
                    ps.setString(5, transaction.getDescription());
                    ps.setTimestamp(6, Timestamp.valueOf(transaction.getTimestamp()));
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao salvar a conta.", e);
        }
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        UUID id = UUID.fromString(rs.getString("id"));
        String accountNumber = rs.getString("account_number");
        String pin = rs.getString("pin");
        Money balance = Money.of(rs.getBigDecimal("balance"));
        Money dailyWithdrawalLimit = Money.of(rs.getBigDecimal("daily_withdrawal_limit"));
        Money totalWithdrawnToday = Money.of(rs.getBigDecimal("total_withdrawn_today"));
        boolean blocked = rs.getInt("blocked") != 0;
        int failedAttempts = rs.getInt("failed_attempts");

        return new Account(id, accountNumber, pin, balance, dailyWithdrawalLimit, totalWithdrawnToday, blocked,
                failedAttempts);
    }

    private void loadTransactions(Connection connection, Account account) throws SQLException {
        String sql = "SELECT id, type, amount, description, created_at FROM tb_transaction "
                + "WHERE account_id = ? ORDER BY created_at ASC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, account.getId().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID transactionId = UUID.fromString(rs.getString("id"));
                    TransactionType type = TransactionType.valueOf(rs.getString("type"));
                    Money amount = Money.of(rs.getBigDecimal("amount"));
                    String description = rs.getString("description");
                    Timestamp createdAt = rs.getTimestamp("created_at");

                    account.seedTransaction(
                            new Transaction(transactionId, createdAt.toLocalDateTime(), type, amount, description));
                }
            }
        }
    }

    private void seedDataIfEmpty() {
        if (!findAll().isEmpty()) {
            return;
        }

        Account acc1 = new Account(UUID.randomUUID(), "12345", "1234", Money.of(5000.00), Money.of(1500.00));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(3),
                TransactionType.DEPOSIT, Money.of(2000.00), "Depósito em dinheiro"));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(2),
                TransactionType.TRANSFER_IN, Money.of(500.00), "Transf. de Conta 67890"));
        acc1.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(1),
                TransactionType.WITHDRAWAL, Money.of(100.00), "Saque eletrônico"));
        save(acc1);

        Account acc2 = new Account(UUID.randomUUID(), "67890", "5678", Money.of(1200.00), Money.of(1000.00));
        acc2.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(5),
                TransactionType.DEPOSIT, Money.of(1500.00), "Depósito inicial"));
        acc2.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(2),
                TransactionType.TRANSFER_OUT, Money.of(500.00), "Transf. para Conta 12345"));
        save(acc2);

        Account acc3 = new Account(UUID.randomUUID(), "99999", "9999", Money.of(50.00), Money.of(500.00));
        acc3.seedTransaction(new Transaction(UUID.randomUUID(), java.time.LocalDateTime.now().minusDays(10),
                TransactionType.DEPOSIT, Money.of(50.00), "Abertura de conta"));
        save(acc3);
    }
}
