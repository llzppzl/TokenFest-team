package com.tokenfest.gateway.config;

import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Blocking controller methods (used by the admin API with JPA) must not run on the Netty
 * event loop.
 */
@Import(BlockingExecutionTests.ThreadController.class)
class BlockingExecutionTests extends IntegrationTestSupport {

	@TestConfiguration
	@RestController
	static class ThreadController {

		@GetMapping("/test/thread")
		String thread() {
			return Thread.currentThread().isVirtual() ? "virtual" : Thread.currentThread().getName();
		}

	}

	@Test
	void blockingControllersRunOnVirtualThreads() {
		client.get().uri("/test/thread").exchange().expectStatus().isOk().expectBody(String.class).isEqualTo("virtual");
	}

}
