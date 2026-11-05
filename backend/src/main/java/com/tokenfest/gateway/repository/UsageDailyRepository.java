package com.tokenfest.gateway.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.tokenfest.gateway.domain.UsageDaily;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageDailyRepository extends JpaRepository<UsageDaily, Long> {

	Optional<UsageDaily> findByUsageDateAndApiKeyIdAndProviderIdAndModel(LocalDate usageDate, Long apiKeyId,
			Long providerId, String model);

	/** Total cost of one key between two dates (inclusive). */
	@Query("select coalesce(sum(u.cost), 0) from UsageDaily u "
			+ "where u.apiKeyId = :apiKeyId and u.usageDate between :from and :to")
	BigDecimal sumCost(@Param("apiKeyId") Long apiKeyId, @Param("from") LocalDate from, @Param("to") LocalDate to);

	List<UsageDaily> findByUsageDateBetween(LocalDate from, LocalDate to);

	List<UsageDaily> findByApiKeyIdAndUsageDateBetween(Long apiKeyId, LocalDate from, LocalDate to);

}
