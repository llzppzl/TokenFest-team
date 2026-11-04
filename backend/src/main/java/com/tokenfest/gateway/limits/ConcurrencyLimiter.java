package com.tokenfest.gateway.limits;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.tokenfest.gateway.keys.KeyInfo;
import com.tokenfest.gateway.proxy.ProxyException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Limits how many requests one key can have running at the same time. Besides protecting
 * the gateway, this keeps a key near its budget from starting many expensive requests at
 * once (spending is only known when a request finishes).
 */
@Component
public class ConcurrencyLimiter {

	private final ConcurrentHashMap<Long, AtomicInteger> running = new ConcurrentHashMap<>();

	private final int maxPerKey;

	public ConcurrencyLimiter(@Value("${tokenfest.max-concurrent-requests-per-key:20}") int maxPerKey) {
		this.maxPerKey = maxPerKey;
	}

	/** Takes a slot or throws 429. Every successful call must be followed by {@link #release}. */
	public void acquire(KeyInfo key) {
		if (maxPerKey <= 0) {
			running.computeIfAbsent(key.id(), id -> new AtomicInteger()).incrementAndGet();
			return;
		}
		AtomicInteger count = running.computeIfAbsent(key.id(), id -> new AtomicInteger());
		if (count.incrementAndGet() > maxPerKey) {
			count.decrementAndGet();
			throw new ProxyException(HttpStatus.TOO_MANY_REQUESTS, "too_many_concurrent_requests",
					"This key already has " + maxPerKey + " requests running. Wait for one to finish.", 1L);
		}
	}

	public void release(KeyInfo key) {
		AtomicInteger count = running.get(key.id());
		if (count != null) {
			count.decrementAndGet();
		}
	}

	/** Requests currently running for a key. */
	public int running(Long keyId) {
		AtomicInteger count = running.get(keyId);
		return count == null ? 0 : count.get();
	}

}
