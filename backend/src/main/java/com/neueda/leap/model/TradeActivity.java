package com.neueda.leap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A filled order as the reporting store keeps it (reporting.trade_activity): copied in once by
 * ReportingLoadService and never changed, with everything reports group by already on the row.
 * Analysts' queries read this, never the live orders table.
 *
 * The id is the live order's id rather than a generated one, so Persistable tells Spring Data that a
 * row made here is new: saving it inserts this same object, instead of looking it up and merging a copy.
 */
@Entity
@Table(schema = "reporting", name = "trade_activity")
public class TradeActivity implements Persistable<Integer> {
    @Id
    @Column(name = "order_id")
    private Integer orderId;

    @Transient
    private boolean isNew = true;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "client_id", nullable = false)
    private Integer clientId;

    @Column(name = "client_segment", nullable = false)
    private String clientSegment;

    @Column(name = "instrument_id", nullable = false)
    private Integer instrumentId;

    @Column(name = "ticker", nullable = false)
    private String ticker;

    @Column(name = "asset_class", nullable = false)
    private String assetClass;

    @Column(name = "market", nullable = false)
    private String market;

    @Column(name = "side", nullable = false)
    private String side;

    @Column(name = "quantity", nullable = false, precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(name = "price", nullable = false, precision = 14, scale = 4)
    private BigDecimal price;

    @Column(name = "notional", nullable = false, precision = 18, scale = 4)
    private BigDecimal notional;

    @Column(name = "loaded_at", nullable = false)
    private LocalDateTime loadedAt;

    public TradeActivity() {
    }

    public TradeActivity(Integer orderId, LocalDateTime executedAt, Integer clientId, String clientSegment,
                         Integer instrumentId, String ticker, String assetClass, String market, String side,
                         BigDecimal quantity, BigDecimal price, LocalDateTime loadedAt) {
        this.orderId = orderId;
        this.executedAt = executedAt;
        this.tradeDate = executedAt.toLocalDate();
        this.clientId = clientId;
        this.clientSegment = clientSegment;
        this.instrumentId = instrumentId;
        this.ticker = ticker;
        this.assetClass = assetClass;
        this.market = market;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.notional = quantity.multiply(price);
        this.loadedAt = loadedAt;
    }

    @Override
    public Integer getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        isNew = false;
    }

    /** Set once, by ReportingLoadService, in the same transaction that stores the row. */
    public void tagSegment(String clientSegment) {
        this.clientSegment = clientSegment;
    }

    public Integer getOrderId() {
        return orderId;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public Integer getClientId() {
        return clientId;
    }

    public String getClientSegment() {
        return clientSegment;
    }

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public String getTicker() {
        return ticker;
    }

    public String getAssetClass() {
        return assetClass;
    }

    public String getMarket() {
        return market;
    }

    public String getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getNotional() {
        return notional;
    }

    public LocalDateTime getLoadedAt() {
        return loadedAt;
    }
}
