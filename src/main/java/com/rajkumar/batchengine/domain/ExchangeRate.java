package com.rajkumar.batchengine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate")
public class ExchangeRate {

    @Id
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "rate_to_gbp", nullable = false, precision = 19, scale = 6)
    private BigDecimal rateToGbp;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ExchangeRate() {
    }

    public ExchangeRate(String currency, BigDecimal rateToGbp) {
        this.currency = currency;
        this.rateToGbp = rateToGbp;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getRateToGbp() {
        return rateToGbp;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
