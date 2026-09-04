package com.neueda.leap;
import java.time.LocalDate;
import java.time.LocalDateTime;
public class PriceQuotes {
    private int price_quote_id;
    private int instrument_id;
    private float price;
    private BigInteger volume;
    private LocalDateTime quote_timestamp;
    private LocalDateTime fetched_at_timestamp;
    public PriceQuotes()
    {
        price_quote_id = 0;
        instrument_id = 0;
        price = 0;
        volume = new BigInteger();
        quote_timestamp = new LocalDateTime();
        fetched_at_timestamp = new LocalDateTime();
    }

    public PriceQuotes(int price_quote_id, int instrument_id, float price, BigInteger volume, 
        LocalDateTime quote_timestamp, LocalDateTime fetched_at_timestamp
    )
    {
        this.price_quote_id = price_quote_id;
        this.instrument_id = instrument_id;
        this.price = price;
        this.volume = volume;
        this.quote_timestamp = quote_timestamp;
        this.fetched_at_timestamp = fetched_at_timestamp;
    }
}
