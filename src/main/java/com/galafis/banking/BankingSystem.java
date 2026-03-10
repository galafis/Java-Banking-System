package com.galafis.banking;

import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Banking System - Account management, transactions, and financial operations.
 *
 * @author Gabriel Demetrios Lafis
 * @version 2.0.0
 */
public class BankingSystem {

    private static final Logger LOGGER = Logger.getLogger(BankingSystem.class.getName());
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final List<Transaction> transactionHistory = new CopyOnWriteArrayList<>();

    public enum AccountType { CHECKING, SAVINGS, BUSINESS }

    public static class Account {
        private final String accountNumber;
        private final String holderName;
        private final AccountType type;
        private double balance;
        private final LocalDateTime openedAt;
        private boolean active;

        public Account(String holderName, AccountType type, double initialDeposit) {
            this.accountNumber = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            this.holderName = holderName;
            this.type = type;
            this.balance = initialDeposit;
            this.openedAt = LocalDateTime.now();
            this.active = true;
        }

        public String getAccountNumber() { return accountNumber; }
        public String getHolderName() { return holderName; }
        public AccountType getType() { return type; }
        public double getBalance() { return balance; }
        public boolean isActive() { return active; }

        synchronized void credit(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            balance += amount;
        }

        synchronized void debit(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            if (amount > balance) throw new IllegalStateException("Insufficient funds");
            balance -= amount;
        }
    }

    public static class Transaction {
        public enum Type { DEPOSIT, WITHDRAWAL, TRANSFER, FEE, INTEREST }

        private final String transactionId;
        private final String fromAccount;
        private final String toAccount;
        private final Type type;
        private final double amount;
        private final String description;
        private final LocalDateTime timestamp;
        private final boolean successful;

        public Transaction(String fromAccount, String toAccount, Type type,
                           double amount, String description, boolean successful) {
            this.transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();
            this.fromAccount = fromAccount;
            this.toAccount = toAccount;
            this.type = type;
            this.amount = amount;
            this.description = description;
            this.timestamp = LocalDateTime.now();
            this.successful = successful;
        }

        public String getTransactionId() { return transactionId; }
        public Type getType() { return type; }
        public double getAmount() { return amount; }
        public boolean isSuccessful() { return successful; }
        public String getFromAccount() { return fromAccount; }
    }

    // ---- Operations ----

    public Account openAccount(String holderName, AccountType type, double initialDeposit) {
        if (initialDeposit < 0) throw new IllegalArgumentException("Initial deposit cannot be negative");
        Account account = new Account(holderName, type, initialDeposit);
        accounts.put(account.getAccountNumber(), account);
        transactionHistory.add(new Transaction(null, account.getAccountNumber(),
                Transaction.Type.DEPOSIT, initialDeposit, "Account opening deposit", true));
        LOGGER.info("Account opened: " + account.getAccountNumber() + " for " + holderName);
        return account;
    }

    public Transaction deposit(String accountNumber, double amount) {
        Account account = getAccount(accountNumber);
        account.credit(amount);
        Transaction txn = new Transaction(null, accountNumber, Transaction.Type.DEPOSIT,
                amount, "Cash deposit", true);
        transactionHistory.add(txn);
        return txn;
    }

    public Transaction withdraw(String accountNumber, double amount) {
        Account account = getAccount(accountNumber);
        try {
            account.debit(amount);
            Transaction txn = new Transaction(accountNumber, null, Transaction.Type.WITHDRAWAL,
                    amount, "Cash withdrawal", true);
            transactionHistory.add(txn);
            return txn;
        } catch (IllegalStateException e) {
            Transaction txn = new Transaction(accountNumber, null, Transaction.Type.WITHDRAWAL,
                    amount, "Failed: " + e.getMessage(), false);
            transactionHistory.add(txn);
            return txn;
        }
    }

    public Transaction transfer(String fromAccountNum, String toAccountNum, double amount) {
        Account from = getAccount(fromAccountNum);
        Account to = getAccount(toAccountNum);
        try {
            from.debit(amount);
            to.credit(amount);
            Transaction txn = new Transaction(fromAccountNum, toAccountNum,
                    Transaction.Type.TRANSFER, amount,
                    "Transfer to " + toAccountNum, true);
            transactionHistory.add(txn);
            return txn;
        } catch (IllegalStateException e) {
            Transaction txn = new Transaction(fromAccountNum, toAccountNum,
                    Transaction.Type.TRANSFER, amount, "Failed: " + e.getMessage(), false);
            transactionHistory.add(txn);
            return txn;
        }
    }

    public void applyInterest(String accountNumber, double annualRate) {
        Account account = getAccount(accountNumber);
        double interest = account.getBalance() * (annualRate / 12.0);
        account.credit(interest);
        transactionHistory.add(new Transaction(null, accountNumber,
                Transaction.Type.INTEREST, interest,
                String.format("Monthly interest at %.2f%% APR", annualRate * 100), true));
    }

    private Account getAccount(String accountNumber) {
        Account account = accounts.get(accountNumber);
        if (account == null) throw new IllegalArgumentException("Account not found: " + accountNumber);
        if (!account.isActive()) throw new IllegalStateException("Account is inactive");
        return account;
    }

    public List<Transaction> getAccountTransactions(String accountNumber) {
        return transactionHistory.stream()
                .filter(t -> accountNumber.equals(t.fromAccount) || accountNumber.equals(t.toAccount))
                .collect(Collectors.toList());
    }

    public Map<String, Object> generateReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalAccounts", accounts.size());
        report.put("totalBalance", accounts.values().stream().mapToDouble(Account::getBalance).sum());
        report.put("totalTransactions", transactionHistory.size());
        report.put("successfulTransactions", transactionHistory.stream().filter(Transaction::isSuccessful).count());
        report.put("totalDeposits", transactionHistory.stream()
                .filter(t -> t.getType() == Transaction.Type.DEPOSIT && t.isSuccessful())
                .mapToDouble(Transaction::getAmount).sum());
        report.put("totalWithdrawals", transactionHistory.stream()
                .filter(t -> t.getType() == Transaction.Type.WITHDRAWAL && t.isSuccessful())
                .mapToDouble(Transaction::getAmount).sum());

        Map<String, Long> byType = accounts.values().stream()
                .collect(Collectors.groupingBy(a -> a.getType().name(), Collectors.counting()));
        report.put("accountsByType", byType);
        return report;
    }

    public static void main(String[] args) {
        System.out.println("=== Java Banking System ===\n");
        BankingSystem bank = new BankingSystem();

        Account acc1 = bank.openAccount("Alice Santos", AccountType.CHECKING, 5000);
        Account acc2 = bank.openAccount("Bruno Lima", AccountType.SAVINGS, 10000);
        Account acc3 = bank.openAccount("Carla Mendes", AccountType.BUSINESS, 25000);

        bank.deposit(acc1.getAccountNumber(), 2500);
        bank.withdraw(acc2.getAccountNumber(), 1500);
        bank.transfer(acc3.getAccountNumber(), acc1.getAccountNumber(), 5000);
        bank.applyInterest(acc2.getAccountNumber(), 0.06);

        System.out.println("Account Balances:");
        System.out.printf("  %s (%s): $%.2f%n", acc1.getHolderName(), acc1.getType(), acc1.getBalance());
        System.out.printf("  %s (%s): $%.2f%n", acc2.getHolderName(), acc2.getType(), acc2.getBalance());
        System.out.printf("  %s (%s): $%.2f%n", acc3.getHolderName(), acc3.getType(), acc3.getBalance());

        System.out.println("\nBank Report:");
        bank.generateReport().forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }
}
