package br.com.diegosilva.bank.actors.account;

import br.com.diegosilva.bank.CborSerializable;

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

    private BankAccountState processTransaction(Transaction t) {
        this.transactions.add(t);
        if (t.type.equals("C")) {
            this.ammount = this.ammount.add(t.amount);
        }

        return this;
    }


    public static final class Transaction implements CborSerializable {
        public final String tid;
        public final long timestamp;
        public final BigDecimal amount;
        public final String from;
        public final String to;
        public final String type;


        public Transaction(String tid,
                           long timestamp,
                           BigDecimal amount,
                           String from,
                           String to,
                           String type) {
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

}
