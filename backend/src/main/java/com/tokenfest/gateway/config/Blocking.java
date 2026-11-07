package com.tokenfest.gateway.config;

import java.util.concurrent.Callable;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Runs blocking code (JPA) from reactive code without blocking the event loop.
 */
public final class Blocking {

	private Blocking() {
	}

	public static <T> Mono<T> call(Callable<T> callable) {
		return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
	}

}
