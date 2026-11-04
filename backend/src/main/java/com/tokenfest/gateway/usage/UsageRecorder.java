package com.tokenfest.gateway.usage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import com.tokenfest.gateway.domain.RequestLog;
import com.tokenfest.gateway.domain.UsageDaily;
import com.tokenfest.gateway.repository.ApiKeyRepository;
import com.tokenfest.gateway.repository.RequestLogRepository;
import com.tokenfest.gateway.repository.UsageDailyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Saves request logs and daily usage in the background, so a slow database never delays a
 * response. A single writer thread means the daily totals can be updated without races.
 *
 * <p>
 * The queue holds at most {@value #QUEUE_CAPACITY} records. If the database is down for a
 * long time, newer records are dropped (and counted) instead of filling up memory.
 */
@Component
public class UsageRecorder implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(UsageRecorder.class);

	static final int QUEUE_CAPACITY = 50_000;

	private volatile Thread writerThread;

	private final ExecutorService writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
				Thread thread = new Thread(runnable, "usage-recorder");
				thread.setDaemon(true);
				writerThread = thread;
				return thread;
			});

	private final AtomicLong dropped = new AtomicLong();

	private final RequestLogRepository requestLogs;

	private final UsageDailyRepository usageDaily;

	private final ApiKeyRepository apiKeys;

	private final TransactionTemplate transaction;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	public UsageRecorder(RequestLogRepository requestLogs, UsageDailyRepository usageDaily, ApiKeyRepository apiKeys,
			TransactionTemplate transaction, Clock clock, ApplicationEventPublisher events) {
		this.requestLogs = requestLogs;
		this.usageDaily = usageDaily;
		this.apiKeys = apiKeys;
		this.transaction = transaction;
		this.clock = clock;
		this.events = events;
	}

	public void record(RequestRecord record) {
		try {
			writer.execute(() -> write(record));
		}
		catch (RejectedExecutionException e) {
			long total = dropped.incrementAndGet();
			if (total == 1 || total % 1000 == 0) {
				log.error("Usage queue is full (database too slow or down); {} request logs dropped so far", total);
			}
		}
	}

	/** Records dropped because the queue was full. */
	public long droppedCount() {
		return dropped.get();
	}

	private void write(RequestRecord record) {
		try {
			transaction.executeWithoutResult(status -> save(record));
		}
		catch (RuntimeException e) {
			log.error("Could not save request log", e);
			return;
		}
		try {
			events.publishEvent(new UsageRecordedEvent(record.apiKeyId(), record.cost(), record.createdAt()));
		}
		catch (RuntimeException e) {
			log.error("Usage listener failed", e);
		}
	}

	private void save(RequestRecord r) {
		RequestLog row = new RequestLog();
		row.setApiKeyId(r.apiKeyId());
		row.setProviderId(r.providerId());
		row.setApiFormat(r.apiFormat());
		row.setModel(r.model());
		row.setStatusCode(r.statusCode());
		row.setStreamed(r.streamed());
		row.setInputTokens((int) r.usage().totalInput());
		row.setOutputTokens((int) r.usage().output());
		row.setCost(r.cost());
		row.setLatencyMs(r.latencyMs());
		row.setAttempts(r.attempts());
		row.setErrorMessage(r.errorMessage());
		row.setRequestBody(r.requestBody());
		row.setResponseBody(r.responseBody());
		row.setCreatedAt(r.createdAt());
		requestLogs.save(row);

		apiKeys.updateLastUsedAt(r.apiKeyId(), r.createdAt());

		// Also requests the client cancelled but that still cost something (estimated)
		if ((r.successful() || r.cost().signum() > 0) && r.providerId() != null && r.model() != null) {
			LocalDate day = LocalDate.ofInstant(r.createdAt(), clock.getZone());
			UsageDaily daily = usageDaily
				.findByUsageDateAndApiKeyIdAndProviderIdAndModel(day, r.apiKeyId(), r.providerId(), r.model())
				.orElseGet(() -> new UsageDaily(day, r.apiKeyId(), r.providerId(), r.model()));
			daily.add(r.usage().totalInput(), r.usage().output(), r.cost());
			usageDaily.save(daily);
		}
	}

	/**
	 * Runs code on the writer thread and waits for the result, so it can't interleave
	 * with saving usage.
	 */
	public <T> T callOnWriter(Callable<T> callable) {
		try {
			if (Thread.currentThread() == writerThread) {
				return callable.call();
			}
			return writer.submit(callable).get(30, TimeUnit.SECONDS);
		}
		catch (RejectedExecutionException e) {
			throw new WriterBusyException(e);
		}
		catch (TimeoutException e) {
			throw new WriterBusyException(e);
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	/** The writer is too busy to run the code (queue full or no answer in 30 seconds). */
	public static class WriterBusyException extends RuntimeException {

		WriterBusyException(Throwable cause) {
			super("Usage writer is busy", cause);
		}

	}

	/** Waits until everything recorded so far is saved. Used by tests. */
	public void awaitIdle() {
		try {
			writer.submit(() -> {
			}).get(10, TimeUnit.SECONDS);
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void destroy() throws InterruptedException {
		writer.shutdown();
		writer.awaitTermination(10, TimeUnit.SECONDS);
	}

}
