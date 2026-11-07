package com.tokenfest.gateway.proxy;

import com.tokenfest.gateway.domain.ProviderType;

/**
 * The proxied endpoints, with the provider API format they belong to and how they are
 * handled.
 */
public enum Endpoint {

	/** OpenAI Chat Completions. */
	CHAT_COMPLETIONS(ProviderType.OPENAI, "/chat/completions", true),

	/** OpenAI Responses API (used by Codex CLI). */
	RESPONSES(ProviderType.OPENAI, "/responses", true),

	/** OpenAI-compatible embeddings. */
	EMBEDDINGS(ProviderType.OPENAI, "/embeddings", true),

	/** Anthropic Messages. */
	MESSAGES(ProviderType.ANTHROPIC, "/v1/messages", true),

	/**
	 * Anthropic token counting (used by Claude Code). Free, so it is not logged and does
	 * not count against the rate limit.
	 */
	COUNT_TOKENS(ProviderType.ANTHROPIC, "/v1/messages/count_tokens", false);

	private final ProviderType format;

	private final String upstreamPath;

	private final boolean billable;

	Endpoint(ProviderType format, String upstreamPath, boolean billable) {
		this.format = format;
		this.upstreamPath = upstreamPath;
		this.billable = billable;
	}

	public ProviderType format() {
		return format;
	}

	/** Appended to the provider's base URL. */
	public String upstreamPath() {
		return upstreamPath;
	}

	/** Logged, priced, and subject to budget and rate limit. */
	public boolean billable() {
		return billable;
	}

}
