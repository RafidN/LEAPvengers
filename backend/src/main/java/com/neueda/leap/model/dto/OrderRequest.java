package com.neueda.leap.model.dto;
import java.math.BigDecimal;
public class OrderRequest {
    private String ticker;
    private BigDecimal quantity;
    public String orderType;
    public OrderRequest(){
    }

    public OrderRequest(String ticker, BigDecimal quantity, String orderType){
        this.ticker = ticker;
        this.quantity = quantity;
        this.orderType = orderType;
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
}
