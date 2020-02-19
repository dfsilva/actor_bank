package br.com.diegosilva.bank.routes.dto;

import java.math.BigDecimal;

public class CreateAccount {

    public final String number;
    public final String uid;
    public final String name;
    public final BigDecimal ammount;

    public CreateAccount(String number, String uid, String name, BigDecimal ammount) {
        this.number = number;
        this.uid = uid;
        this.name = name;
        this.ammount = ammount;
    }
}