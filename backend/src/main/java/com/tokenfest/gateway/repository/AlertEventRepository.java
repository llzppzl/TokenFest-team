package com.tokenfest.gateway.repository;

import java.util.List;

import com.tokenfest.gateway.domain.AlertEvent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long> {

	boolean existsByApiKeyIdAndBudgetMonthAndThresholdPercent(Long apiKeyId, String budgetMonth, int thresholdPercent);

	List<AlertEvent> findTop100ByOrderByCreatedAtDesc();

}
