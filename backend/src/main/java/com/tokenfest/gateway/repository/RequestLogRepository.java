package com.tokenfest.gateway.repository;

import java.time.Instant;

import com.tokenfest.gateway.domain.RequestLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long>, JpaSpecificationExecutor<RequestLog> {

	@Modifying
	@Query("delete from RequestLog r where r.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);

}
