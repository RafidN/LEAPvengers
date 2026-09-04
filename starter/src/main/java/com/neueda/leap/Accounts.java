package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class CashTransactions {
    private int account_id;
    private int client_id;
    private LocalDate opened_date;
    private double balance;
    public CashTransactions()
    {
        account_id = 0;
        client_id = 0;
        opened_date = new LocalDate();
        balance = 0;
    }

    public CashTransactions(int account_id, int client_id, LocalDate opened_date, float balance)
    {
        this.account_id = account_id;
        this.client_id = client_id;
        this.opened_date = opened_date;
        this.balance = balance;
    }
}
