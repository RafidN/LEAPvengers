package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class Orders {
    private int order_id;
    private int account_id;
    private int instrument_id;
    private String order_type;
    private double quantity;
    private double price;
    private LocalDate order_date;
    private String order_status;
    private LocalDateTime submitted_at_timestamp;
    private LocalDateTime executed_at_timestamp;
    public Orders()
    {
        order_id = 0;
        account_id = 0;
        instrument_id = 0;
        order_type = "";
        quantity = 0;
        price = 0;
        order_date = new LocalDate();
        order_status  "";
        submitted_at_timestamp = new LocalDateTime();
        executed_at_timestamp = new LocalDateTime();
    }

    public CashTransactions(int order_id, int account_id, int instrument_id,
        String order_type, double quantity, double price, LocalDate order_date,
        String order_status, LocalDateTime submitted_at_timestamp, LocalDateTime executed_at_timestamp
    )
    {
        this.order_id = order_id;
        this.account_id = account_id;
        this.instrument_id = instrument_id;
        this.order_type = order_type;
        this.quantity = quantity;
        this.price = price;
        this.order_date = order_date
        this.order_status = order_status
        this.submitted_at_timestamp = submitted_at_timestamp;
        this.executed_at_timestamp = executed_at_timestamp;
    }
}
