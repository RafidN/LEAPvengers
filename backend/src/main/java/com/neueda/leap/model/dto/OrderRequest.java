package com.neueda.leap.model.dto;
import java.math.BigDecimal;
import com.neueda.leap.model.Accounts;

public class OrderRequest {
    private String ticker;
    private BigDecimal quantity;
    private String orderType;
    private Accounts account;
    public OrderRequest(){
    }

    public OrderRequest(String ticker, BigDecimal quantity, String orderType, Accounts account){
        this.ticker = ticker;
        this.quantity = quantity;
        this.orderType = orderType;
        this.account = account;
    }

    public String getTicker(){
        return ticker;
    }

    public void setTicker(String ticker){
        this.ticker = ticker;
    }

    public BigDecimal getQuantity(){
        return quantity;
    }

    public void setQuantity(BigDecimal quantity){
        this.quantity = quantity;
    }

    public String getOrderType(){
        return orderType;
    }

    public void setOrderType(String orderType){
        this.orderType = orderType;
    }

    public Accounts getAccount(){
        return account;
    }

    public void setAccount(Accounts account){
        this.account = account;
    }
}
