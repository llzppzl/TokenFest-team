package com.tokenfest.gateway.repository;

import java.util.List;
import java.util.Optional;

import com.tokenfest.gateway.domain.Provider;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {

	@EntityGraph(attributePaths = "models")
	List<Provider> findAllByOrderByPriorityAscIdAsc();

	@EntityGraph(attributePaths = "models")
	List<Provider> findByEnabledTrueOrderByPriorityAscIdAsc();

	@EntityGraph(attributePaths = "models")
	Optional<Provider> findWithModelsById(Long id);

	boolean existsByName(String name);

}
