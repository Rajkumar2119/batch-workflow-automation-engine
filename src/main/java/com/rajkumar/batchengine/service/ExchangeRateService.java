package com.rajkumar.batchengine.service;

import com.rajkumar.batchengine.config.CacheConfig;
import com.rajkumar.batchengine.domain.ExchangeRate;
import com.rajkumar.batchengine.repository.ExchangeRateRepository;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class ExchangeRateService {

    private final ExchangeRateRepository repository;

    public ExchangeRateService(ExchangeRateRepository repository) {
        this.repository = repository;
    }

    /**
     * Rate that converts one unit of {@code currency} into GBP.
     * Cached per currency, so a feed of a million rows makes a handful of DB calls.
     */
    @Cacheable(cacheNames = CacheConfig.FX_RATES, key = "#currency.toUpperCase()")
    public Optional<BigDecimal> rateToGbp(String currency) {
        return repository.findById(currency.toUpperCase(Locale.ROOT)).map(ExchangeRate::getRateToGbp);
    }
}
