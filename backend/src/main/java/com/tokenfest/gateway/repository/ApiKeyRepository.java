package com.tokenfest.gateway.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.tokenfest.gateway.domain.ApiKey;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

	@EntityGraph(attributePaths = "allowedProviders")
	Optional<ApiKey> findByKeyHash(String keyHash);

	@EntityGraph(attributePaths = "allowedProviders")
	Optional<ApiKey> findWithProvidersById(Long id);

	@EntityGraph(attributePaths = "allowedProviders")
	List<ApiKey> findAllByOrderByIdAsc();

	@Modifying
	@Query("update ApiKey k set k.lastUsedAt = :time where k.id = :id")
	void updateLastUsedAt(@Param("id") Long id, @Param("time") Instant time);

}
