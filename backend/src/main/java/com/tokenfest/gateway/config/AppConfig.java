package com.tokenfest.gateway.config;

import java.time.Clock;
import java.time.Duration;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.reactive.config.BlockingExecutionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableScheduling
public class AppConfig implements WebFluxConfigurer {

	/**
	 * Controller methods that don't return Mono/Flux (the admin API, which uses JPA) run
	 * on virtual threads instead of blocking the Netty event loop.
	 */
	@Override
	public void configureBlockingExecution(BlockingExecutionConfigurer configurer) {
		configurer.setExecutor(new VirtualThreadTaskExecutor("blocking-"));
	}

	@Bean
	Clock clock(TokenFestProperties properties) {
		return Clock.system(properties.timeZone());
	}

	/**
	 * Connections to LLM providers. A stream holds its connection for minutes, so the pool
	 * is much larger than Reactor Netty's shared default (about 16 per provider).
	 */
	@Bean(destroyMethod = "dispose")
	ConnectionProvider upstreamConnections(@Value("${tokenfest.upstream.max-connections:500}") int maxConnections) {
		return ConnectionProvider.builder("upstream")
			.maxConnections(maxConnections)
			.pendingAcquireMaxCount(maxConnections * 2)
			.pendingAcquireTimeout(Duration.ofSeconds(30))
			// Providers close idle keep-alive connections; drop ours first
			.maxIdleTime(Duration.ofSeconds(50))
			.evictInBackground(Duration.ofSeconds(30))
			.build();
	}

	/** WebClient used to call LLM providers. */
	@Bean
	WebClient upstreamWebClient(TokenFestProperties properties, ConnectionProvider upstreamConnections) {
		HttpClient httpClient = HttpClient.create(upstreamConnections)
			.option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
					(int) properties.upstream().connectTimeout().toMillis())
			.responseTimeout(properties.upstream().readTimeout());
		return WebClient.builder()
			.clientConnector(new ReactorClientHttpConnector(httpClient))
			.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
			.build();
	}

}
