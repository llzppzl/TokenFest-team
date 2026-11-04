package com.tokenfest.gateway.maintenance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.tokenfest.gateway.repository.RequestLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes request logs (with their bodies) older than {@code tokenfest.log-retention-days}
 * every night. Daily totals, budgets and alerts are kept, so charts and exports still
 * work for older months.
 */
@Component
public class LogCleanup {

	private static final Logger log = LoggerFactory.getLogger(LogCleanup.class);

	private final RequestLogRepository requestLogs;

	private final Clock clock;

	private final int retentionDays;

	public LogCleanup(RequestLogRepository requestLogs, Clock clock,
			@Value("${tokenfest.log-retention-days:90}") int retentionDays) {
		this.requestLogs = requestLogs;
		this.clock = clock;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "${tokenfest.cleanup-cron:0 30 3 * * *}", zone = "${tokenfest.time-zone:UTC}")
	@Transactional
	public int deleteOldLogs() {
		if (retentionDays <= 0) {
			return 0; // keep forever
		}
		Instant cutoff = Instant.now(clock).minus(Duration.ofDays(retentionDays));
		int deleted = requestLogs.deleteByCreatedAtBefore(cutoff);
		if (deleted > 0) {
			log.info("Deleted {} request logs older than {} days", deleted, retentionDays);
		}
		return deleted;
	}

}
