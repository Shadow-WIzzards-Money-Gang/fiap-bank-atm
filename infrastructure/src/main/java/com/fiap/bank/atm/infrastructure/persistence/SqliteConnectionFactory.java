package com.fiap.bank.atm.infrastructure.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Fábrica responsável por fornecer e encerrar as conexões com o arquivo do banco de dados SQLite,
 * além de garantir a criação do esquema (DDL) na inicialização.
 */
public final class SqliteConnectionFactory {
    private static final String DB_URL = "jdbc:sqlite:fiap-bank-atm.db";

    public SqliteConnectionFactory() {
        initializeSchema();
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL);
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    public void closeConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao encerrar a conexão com o banco de dados.", e);
        }
    }

    private void initializeSchema() {
        String createAccountTable = """
                CREATE TABLE IF NOT EXISTS tb_account (
                    id VARCHAR(36) PRIMARY KEY,
                    account_number VARCHAR(20) NOT NULL UNIQUE,
                    pin VARCHAR(10) NOT NULL,
                    balance DECIMAL(15, 2) NOT NULL,
                    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
                    total_withdrawn_today DECIMAL(15, 2) NOT NULL,
                    blocked INTEGER NOT NULL,
                    failed_attempts INTEGER NOT NULL
                )
                """;

        String createTransactionTable = """
                CREATE TABLE IF NOT EXISTS tb_transaction (
                    id VARCHAR(36) PRIMARY KEY,
                    account_id VARCHAR(36) NOT NULL,
                    type VARCHAR(20) NOT NULL,
                    amount DECIMAL(15, 2) NOT NULL,
                    description VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    FOREIGN KEY (account_id) REFERENCES tb_account(id)
                )
                """;

        try (Connection connection = DriverManager.getConnection(DB_URL);
                Statement statement = connection.createStatement()) {
            statement.execute(createAccountTable);
            statement.execute(createTransactionTable);
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao inicializar o esquema do banco de dados.", e);
        }
    }
}
