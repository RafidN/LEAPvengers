package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class Accounts {
    private int account_id;
    private int client_id;
    private LocalDate opened_date;
    private double balance;
    public Accounts()
    {
        account_id = 0;
        client_id = 0;
        opened_date = new LocalDate();
        balance = 0;
    }

    public Accounts(int account_id, int client_id, LocalDate opened_date, double balance)
    {
        this.account_id = account_id;
        this.client_id = client_id;
        this.opened_date = opened_date;
        this.balance = balance;
    }
}
