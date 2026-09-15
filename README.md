# FIAP Bank - Emulador de Caixa Eletrônico (ATM)

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=java)
![Maven](https://img.shields.io/badge/Maven-3.x-blue?style=for-the-badge&logo=apache-maven)
![SQLite](https://img.shields.io/badge/JDBC-SQLite-003B57?style=for-the-badge&logo=sqlite)
![FlatLaf](https://img.shields.io/badge/UI-FlatLaf_Dark-darkgreen?style=for-the-badge)
![Architecture](https://img.shields.io/badge/Architecture-Domain--Driven_Design_(DDD)-purple?style=for-the-badge)

> **FIAP - Engenharia de Software (2026)**
> **Checkpoint 4 (CP4)** — Refatoração arquitetural do emulador de Caixa Eletrônico (ATM), aplicando **Domain-Driven Design (DDD)**, **modularização física via Maven**, **persistência JDBC/SQLite** e **paradigma funcional (Optional/Streams)**, mantendo o front-end original em **Java Swing (FlatLaf)** 100% intacto.

---

## 👥 Integrantes do Grupo

|     RM     | Nome Completo |
| `RM563524` | Felipe Bicaletto |
| `RM561777` | Antônio Neto |
| `RM556645` | Mauro Carlos |

---

## 📌 Visão Geral

O **FIAP Bank ATM** é um emulador interativo de Caixa Eletrônico com alta fidelidade visual e comportamental, capaz de autenticar contas via número/PIN, realizar saques, depósitos, transferências entre contas e emitir extrato — tudo através de uma interface Swing que simula um terminal bancário real.

O projeto chegou como um monólito violando as fronteiras de camadas do DDD (apresentação acessando diretamente o domínio, persistência volátil em memória, uso de `null` em vez de estruturas defensivas). Este Checkpoint refatora o "motor" da aplicação nos bastidores sem alterar uma linha do front-end Swing, entregando:

- **Isolamento físico em módulos Maven** (`domain`, `application`, `infrastructure`, `presentation`), com a apresentação enxergando exclusivamente a camada de `application`.
- **Contratos de transferência de dados (DTOs)** como `Java Records`, blindando as entidades de domínio contra exposição direta.
- **Repositório genérico** (`ATMRepository<T extends BaseEntity>`) com retorno padronizado em `Optional<T>`, eliminando `null`.
- **Persistência real e durável** em banco de dados relacional **SQLite**, via **JDBC puro** (sem ORM), com `PreparedStatement`/`ResultSet` em todas as rotinas transacionais.

---

## 🎯 Principais Funcionalidades

- **🔐 Autenticação Segura & Gestão de PIN**: validação de conta/PIN, bloqueio automático após 3 tentativas incorretas, reset de tentativas ao autenticar com sucesso.
- **💵 Saque Eletrônico**: valores rápidos pré-definidos ou customizados, validação de saldo (`InsufficientFundsException`) e de limite diário (`DailyLimitExceededException`).
- **📥 Depósito em Dinheiro**: entrada de valores via teclado com validação de valor mínimo positivo.
- **💸 Transferência entre Contas**: valida conta de destino, contas bloqueadas e proíbe transferência para a própria conta; lança automaticamente `TRANSFER_OUT`/`TRANSFER_IN`.
- **📊 Consulta de Saldo e Limite Diário**: exibição formatada em `R$ X.XXX,XX`.
- **🧾 Extrato**: emissão de extrato em pop-up estilo comprovante térmico com as últimas transações.
- **⌨️ Interação dupla**: botões virtuais/laterais e teclado físico do computador.

*(Toda a experiência visual acima é herdada do front-end original em Swing, que não foi alterado nesta refatoração.)*

---

## 🏗️ Arquitetura do Sistema (Módulos Maven / DDD)

O projeto é um **agregador Maven** (`pom.xml` raiz com `packaging=pom`) dividido em 4 módulos físicos, cada um com seu próprio `pom.xml` e árvore `src/main/java`:

```
fiap-bank-atm/                        (pom agregador)
│
├── domain/                           # Regras de negócio puras — SEM dependências externas
│   └── com.fiap.bank.atm.domain
│       ├── exception/                # AccountBlockedException, DailyLimitExceededException,
│       │                             # InsufficientFundsException, InvalidPinException
│       ├── model/                    # Account, BaseEntity, Money, Transaction, TransactionType
│       └── repository/               # ATMRepository<T extends BaseEntity>, AccountRepository
│
├── application/                      # Casos de uso, orquestração e contratos (DTOs)
│   └── com.fiap.bank.atm.application
│       ├── dto/                      # AccountInfoDTO, TransactionDTO (Java Records)
│       └── service/                  # AtmService (único ponto de entrada da lógica de negócio)
│
├── infrastructure/                   # Persistência e recursos externos
│   └── com.fiap.bank.atm.infrastructure.persistence
│       ├── SqliteConnectionFactory   # Fábrica de conexões JDBC + criação do schema (DDL)
│       ├── AccountRepositoryJdbcImpl # Implementação JDBC (PreparedStatement/ResultSet) — persistência real
│       └── InMemoryAccountRepository # Implementação em memória (mantida como referência)
│
└── presentation/                     # UI Swing — depende exclusivamente de `application`
    └── com.fiap.bank.atm(.presentation)
        ├── AtmApplication.java       # Composition root: monta repositório + service + tela
        ├── AtmFrame.java / .form     # Janela principal (FlatLaf Dark Theme) — INTOCADA
        └── ScreenState.java          # Máquina de estados finitos da tela
```

### Regra de dependências (blindada em tempo de compilação)

```
presentation ──▶ application ──▶ domain
                       │
                       └────────▶ infrastructure ──▶ domain
```

`presentation/pom.xml` declara dependência **apenas** de `application` — nenhuma tag aponta para `domain` ou `infrastructure`. O módulo `domain` não possui nenhuma dependência de saída (nem de `infrastructure`, nem de `application`).

---

## 🗄️ Persistência de Dados (JDBC + SQLite)

A camada de infraestrutura persiste os dados em um arquivo de banco de dados relacional **SQLite** (`fiap-bank-atm.db`, criado automaticamente na raiz do projeto na primeira execução), utilizando **exclusivamente a API JDBC nativa** — sem Hibernate, JPA, Spring Data ou qualquer outro ORM.

- **`SqliteConnectionFactory`**: abre/fecha conexões (`Connection`) com o arquivo `.db` e garante a criação das tabelas (`CREATE TABLE IF NOT EXISTS`) na inicialização.
- **`AccountRepositoryJdbcImpl`**: implementa `AccountRepository`, usando **somente** `PreparedStatement` (nunca `Statement` com concatenação de String, prevenindo SQL Injection) e `ResultSet` para mapear linhas do banco de volta para os objetos de domínio (`Account`/`Transaction`).
- Todas as buscas retornam `Optional<Account>` — nunca `null`.
- Na primeira execução (banco vazio), o repositório semeia automaticamente as 3 contas de teste abaixo.

### Esquema do Banco (DDL)

```sql
CREATE TABLE IF NOT EXISTS tb_account (
    id VARCHAR(36) PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    pin VARCHAR(10) NOT NULL,
    balance DECIMAL(15, 2) NOT NULL,
    daily_withdrawal_limit DECIMAL(15, 2) NOT NULL,
    total_withdrawn_today DECIMAL(15, 2) NOT NULL,
    blocked INTEGER NOT NULL,
    failed_attempts INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS tb_transaction (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    FOREIGN KEY (account_id) REFERENCES tb_account(id)
);
```

> O esquema foi adaptado a partir do dicionário de dados de referência do enunciado para refletir fielmente os campos já existentes na entidade `Account` do domínio (PIN, limite diário, total sacado no dia, tentativas falhas), preservando as tabelas `tb_account`/`tb_transaction` e o uso de `PreparedStatement`/`ResultSet`.

### Trocando a implementação do repositório

A troca de infraestrutura acontece em um único ponto — o *composition root* em [`AtmApplication.java`](presentation/src/main/java/com/fiap/bank/atm/AtmApplication.java):

```java
SqliteConnectionFactory connectionFactory = new SqliteConnectionFactory();
AccountRepository accountRepository = new AccountRepositoryJdbcImpl(connectionFactory);
AtmService atmService = new AtmService(accountRepository);
```

Isso ilustra o objetivo central do Checkpoint: a tela (Swing) continua funcionando de forma idêntica para o usuário final, sem perceber que o "motor" por trás — antes em memória (`InMemoryAccountRepository`), agora em banco relacional (`AccountRepositoryJdbcImpl`) — foi completamente substituído.

---

## 🔑 Contas Pré-cadastradas para Teste (Seed Data)

Carregadas automaticamente no banco SQLite na primeira execução (caso a tabela `tb_account` esteja vazia):

| Número da Conta | PIN (Senha) | Saldo Inicial | Limite Diário Saque | Histórico Inicial |
| :---: | :---: | :---: | :---: | :--- |
| **`12345`** | `1234` | **R\$ 5.000,00** | R\$ 1.500,00 | Depósito R\$ 2.000, Transf. Recebida R\$ 500, Saque R\$ 100 |
| **`67890`** | `5678` | **R\$ 1.200,00** | R\$ 1.000,00 | Depósito R\$ 1.500, Transf. Enviada R\$ 500 |
| **`99999`** | `9999` | **R\$ 50,00** | R\$ 500,00 | Depósito R\$ 50,00 (Abertura de conta) |

---

## 🛠️ Tecnologias e Bibliotecas Utilizadas

- **Java 21**: linguagem principal (LTS).
- **Apache Maven** (multi-módulo/agregador): build e gerenciamento de dependências.
- **Swing (Java GUI)** + **FlatLaf 3.5.1**: interface gráfica original, preservada integralmente.
- **JDBC nativo** (`java.sql.*`) + **SQLite JDBC Driver** (`org.xerial:sqlite-jdbc:3.45.1.0`): persistência relacional sem ORM.
- **SLF4J (`slf4j-nop`)**: implementação de logging *no-op*, exigida em tempo de execução pelo driver do SQLite.
- **JUnit 5 (5.10.2)**: testes unitários.

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
- **JDK 21** ou superior, configurado em `JAVA_HOME`.
- **Apache Maven 3.8+**.

### Linha de Comando (Terminal / Prompt)

1. Na raiz do projeto, compile e instale todos os módulos no repositório local (necessário em builds multi-módulo, para que `presentation` enxergue os artefatos de `application`/`domain`/`infrastructure`):
   ```bash
   mvn clean install
   ```

2. Execute a aplicação a partir do módulo `presentation` (que já possui o `exec-maven-plugin` configurado apontando para `com.fiap.bank.atm.AtmApplication`):
   ```bash
   mvn -pl presentation exec:java
   ```

3. Na primeira execução, o arquivo `fiap-bank-atm.db` será criado automaticamente na raiz do projeto, junto com as contas de teste listadas acima.

### IDE (NetBeans / IntelliJ IDEA / Eclipse / VS Code)

1. Abra a IDE e selecione **Open Project** apontando para a pasta raiz (onde está o `pom.xml` agregador).
2. Aguarde a sincronização das dependências Maven de todos os módulos.
3. Execute a classe [`AtmApplication.java`](presentation/src/main/java/com/fiap/bank/atm/AtmApplication.java) (`com.fiap.bank.atm.AtmApplication`), localizada no módulo `presentation`.

---

## 🧪 Rodando os Testes

```bash
mvn test
```

---

## 📝 Licença e Créditos

Desenvolvido para fins acadêmicos como parte do curso de **Engenharia de Software (2026)** da **FIAP**, disciplina **Domain-Driven Design - Java**.
Prof. Eduardo dos Santos Ramos.
