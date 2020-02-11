package br.com.diegosilva.bank.states;

import br.com.diegosilva.bank.CborSerializable;
import br.com.diegosilva.bank.actors.account.BankAccount;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * The state for the {@link BankAccount} entity.
 */

public final class BankAccountState implements CborSerializable {

    private String accountNumber;
    private String ownerName;
    private Set<Transaction> transactions = new HashSet<>();
    private BigDecimal ammount = BigDecimal.ZERO;

    public BankAccountState() { ;
    }

    public BankAccountState(String accountNumber, String ownerName) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
    }

    public static final class Transaction implements CborSerializable {
        public final String tid;
        public final long timestamp;
        public final BigDecimal amount;
        public final String from;
        public final String to;
        public final String type;


        public Transaction(String tid, long timestamp, BigDecimal amount, String from, String to, String type) {
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
