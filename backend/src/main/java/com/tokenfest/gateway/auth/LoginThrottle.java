package com.tokenfest.gateway.auth;

import java.net.InetSocketAddress;
import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tokenfest.gateway.web.ApiException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

/**
 * Slows down password and key guessing: at most 10 login attempts per minute from one
 * client address.
 */
@Component
public class LoginThrottle {

	static final int ATTEMPTS_PER_MINUTE = 10;

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.expireAfterAccess(Duration.ofMinutes(10))
		.maximumSize(100_000)
		.build();

	public void check(ServerHttpRequest request) {
		InetSocketAddress remote = request.getRemoteAddress();
		String client = remote != null && remote.getAddress() != null ? remote.getAddress().getHostAddress() : "unknown";
		Bucket bucket = buckets.get(client, ignored -> Bucket.builder()
			.addLimit(Bandwidth.builder()
				.capacity(ATTEMPTS_PER_MINUTE)
				.refillIntervally(ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1))
				.build())
			.build());
		if (!bucket.tryConsume(1)) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Wait a minute and try again.");
		}
	}

	/** Clears all counters. Used by tests. */
	public void reset() {
		buckets.invalidateAll();
	}

}
