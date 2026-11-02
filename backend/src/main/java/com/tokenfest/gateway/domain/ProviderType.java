package com.tokenfest.gateway.domain;

/**
 * API format spoken by a provider. A request sent to the OpenAI endpoint is only routed
 * to OPENAI providers, and the same for ANTHROPIC.
 */
public enum ProviderType {
	OPENAI, ANTHROPIC
}
