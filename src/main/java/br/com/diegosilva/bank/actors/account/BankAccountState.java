package br.com.diegosilva.bank.actors.account;

import br.com.diegosilva.bank.CborSerializable;
import br.com.diegosilva.bank.domain.TransactionType;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The state for the {@link BankAccount} entity.
 */

public final class BankAccountState implements CborSerializable {

    private String number;
    private String name;
    private String uid;
    private BigDecimal ammount = BigDecimal.ZERO;
    private Set<Transaction> transactions = new HashSet<>();


    public BankAccountState() {
    }

    public BankAccountState createAccount(String number, String name, String uid, Transaction transaction) {
        this.number = number;
        this.name = name;
        this.uid = uid;
        return processTransaction(transaction);
    }

    public BankAccountState processTransaction(Transaction t) {
        this.transactions.add(t);

        if (t.type == TransactionType.C) {
            if (t.amount != null)
                this.ammount = this.ammount.add(t.amount);
        }

        if (t.type == TransactionType.D) {
            if (t.amount != null)
                this.ammount = this.ammount.subtract(t.amount);
        }

        return this;
    }

    public boolean hasMoney(BigDecimal ammount) {
        return this.ammount.compareTo(ammount) >= 0;
    }

    boolean isCreated() {
        return number != null && !number.isEmpty();
    }


    public static final class Transaction implements CborSerializable {
        public final String tid;
        public final long timestamp;
        public final BigDecimal amount;
        public final String from;
        public final String to;
        public final TransactionType type;


        public Transaction(String tid,
                           long timestamp,
                           BigDecimal amount,
                           String from,
                           String to,
                           TransactionType type) {
            this.tid = tid;
            this.timestamp = timestamp;
            this.amount = amount;
            this.from = from;
            this.to = to;
            this.type = type;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Transaction that = (Transaction) o;
            return tid.equals(that.tid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tid);
        }
    }

    @Override
    public String toString() {
        return "BankAccountState{" +
                "number='" + number + '\'' +
                ", name='" + name + '\'' +
                ", uid='" + uid + '\'' +
                ", ammount=" + ammount +
                ", transactions=" + transactions +
                '}';
    }
}
