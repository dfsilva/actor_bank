package br.com.diegosilva.bank.routes.dto;

import br.com.diegosilva.bank.domain.TransactionType;

import java.math.BigDecimal;

public class MakeTransaction {

    public TransactionType type;
    public BigDecimal ammount;
    public String from;
    public String to;
    public String description;

    public MakeTransaction(TransactionType type, BigDecimal ammount, String from, String to, String description) {
        this.type = type;
        this.ammount = ammount;
        this.from = from;
        this.to = to;
        this.description = description;
    }
}
