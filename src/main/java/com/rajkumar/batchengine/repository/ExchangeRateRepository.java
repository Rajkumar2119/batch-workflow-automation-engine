package com.rajkumar.batchengine.repository;

import com.rajkumar.batchengine.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, String> {
}
