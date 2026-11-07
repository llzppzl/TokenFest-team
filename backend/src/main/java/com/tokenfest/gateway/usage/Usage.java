package com.tokenfest.gateway.usage;

import tools.jackson.databind.JsonNode;

/**
 * Token counts for one request. For Anthropic, {@code input} does not include cached
 * tokens, which are counted separately because they are priced differently.
 */
public record Usage(long input, long output, long cacheWrite, long cacheRead) {

	public static final Usage ZERO = new Usage(0, 0, 0, 0);

	/** All input tokens, including cached ones. */
	public long totalInput() {
		return input + cacheWrite + cacheRead;
	}

	/** From an OpenAI {@code usage} object. */
	public static Usage fromOpenAi(JsonNode usage) {
		return new Usage(usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0), 0, 0);
	}

	/** From an OpenAI Responses API {@code usage} object. */
	public static Usage fromResponses(JsonNode usage) {
		return new Usage(usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0), 0, 0);
	}

	/** From an Anthropic {@code usage} object. */
	public static Usage fromAnthropic(JsonNode usage) {
		return new Usage(usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0),
				usage.path("cache_creation_input_tokens").asLong(0), usage.path("cache_read_input_tokens").asLong(0));
	}

	/**
	 * Fills in an estimate (about 4 characters per token) when a stream ended before the
	 * provider reported its final numbers. Reported numbers are never lowered.
	 */
	public Usage withEstimate(long inputChars, long outputChars) {
		boolean inputKnown = input > 0 || cacheWrite > 0 || cacheRead > 0;
		long estimatedInput = inputKnown ? input : (inputChars + 3) / 4;
		long estimatedOutput = Math.max(output, (outputChars + 3) / 4);
		return new Usage(estimatedInput, estimatedOutput, cacheWrite, cacheRead);
	}

	/** Keeps the larger value of each field (Anthropic streams send running totals). */
	public Usage max(Usage other) {
		return new Usage(Math.max(input, other.input), Math.max(output, other.output),
				Math.max(cacheWrite, other.cacheWrite), Math.max(cacheRead, other.cacheRead));
	}

}
