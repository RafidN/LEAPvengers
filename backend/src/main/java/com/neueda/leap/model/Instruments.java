package com.neueda.leap;

public class Instruments {
    private int instrument_id;
    private String ticker;
    private String instrument_name;
    private String asset_class;
    public Instruments()
    {
       instrument_id = 0;
       ticker = "";
       instrument_name = "";
       asset_class = "";
    }

    public Holdings(int instrument_id, String ticker, String instrument_name, String asset_class)
    {
        this.instrument_id = instrument_id;
        this.ticker = ticker;
        this.instrument_name = instrument_name;
        this.asset_class = asset_class;
    }
}
