package com.fiap.bank.atm;

import com.fiap.bank.atm.application.service.AtmService;
import com.fiap.bank.atm.domain.repository.AccountRepository;
import com.fiap.bank.atm.infrastructure.persistence.AccountRepositoryJdbcImpl;
import com.fiap.bank.atm.infrastructure.persistence.SqliteConnectionFactory;
import com.fiap.bank.atm.presentation.AtmFrame;
import javax.swing.SwingUtilities;

public class AtmApplication {
    public static void main(String[] args) {
        // Inicializa as camadas de Infraestrutura e Aplicação (DDD)
        SqliteConnectionFactory connectionFactory = new SqliteConnectionFactory();
        AccountRepository accountRepository = new AccountRepositoryJdbcImpl(connectionFactory);
        AtmService atmService = new AtmService(accountRepository);

        // Inicializa a camada de Apresentação de forma segura na Event Dispatch Thread
        // (EDT)
        SwingUtilities.invokeLater(() -> {
            AtmFrame mainFrame = new AtmFrame(atmService);
            mainFrame.setVisible(true);
        });
    }
}
