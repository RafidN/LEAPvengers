package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class Holdings {
    private int holding_id;
    private int account_id;
    private int instrument_id;
    private double quantity;
    private LocalDate as_of_date;
    public Holdings()
    {
        holding_id = 0;
        account_id = 0;
        instrument_id = 0;
        quantity = 0;
        as_of_date = new LocalDate();
    }

    public Holdings(int account_id, int client_id, LocalDate opened_date, float balance)
    {
        this.account_id = account_id;
        this.client_id = client_id;
        this.opened_date = opened_date;
        this.balance = balance;
    }
}
