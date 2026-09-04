package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class CashTransactions {
    private int cash_transactions_id;
    private int account_id;
    private String txn_type;
    //Could use DecimalFormat class if want to standardize decimal places but wouldn't recommend
    private double amount;
    private LocalDate txn_date;
    public CashTransactions()
    {
        cash_transactions_id = 0;
        account_id = 0;
        txn_type = "";
        amount = 0;
        txn_date = new LocalDate();
    }

    public CashTransactions(int cash_transactions_id, int account_id, String txn_type, double amount, LocalDate txn_date)
    {
        this.cash_transactions_id = cash_transactions_id;
        this. account_id = account_id;
        this.txn_type = txn_type;
        this.amount = amount;
        this.txn_date = txn_date;
    }
}
