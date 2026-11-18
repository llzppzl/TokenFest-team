package com.tokenfest.gateway.proxy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.tokenfest.gateway.usage.Usage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SseProcessorTests {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final String OPENAI_STREAM = """
			data: {"id":"1","choices":[{"index":0,"delta":{"content":"Hel"}}]}

			data: {"id":"1","choices":[{"index":0,"delta":{"content":"lo"}}]}

			data: {"id":"1","choices":[],"usage":{"prompt_tokens":11,"completion_tokens":2,"total_tokens":13}}

			data: [DONE]

			""";

	private static final String ANTHROPIC_STREAM = """
			event: message_start
			data: {"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":20,"cache_creation_input_tokens":100,"cache_read_input_tokens":1000,"output_tokens":1}}}

			event: content_block_delta
			data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

			event: message_delta
			data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":42}}

			event: message_stop
			data: {"type":"message_stop"}

			""";

	/** Feeds the text in pieces of the given size and returns what the client receives. */
	private static String run(SseProcessor sse, String text, int pieceSize) {
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		List<byte[]> out = new ArrayList<>();
		for (int i = 0; i < bytes.length; i += pieceSize) {
			byte[] piece = java.util.Arrays.copyOfRange(bytes, i, Math.min(bytes.length, i + pieceSize));
			out.addAll(sse.onBytes(piece));
		}
		out.addAll(sse.finish());
		StringBuilder sb = new StringBuilder();
		out.forEach(b -> sb.append(new String(b, StandardCharsets.UTF_8)));
		return sb.toString();
	}

	@Test
	void openAiUsageIsReadAndUsageChunkDropped() {
		for (int size : new int[] { 1, 7, 64, 10_000 }) {
			SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, true, JSON, 100_000);
			String out = run(sse, OPENAI_STREAM, size);
			assertThat(sse.usage()).isEqualTo(new Usage(11, 2, 0, 0));
			assertThat(out).contains("Hel", "lo", "[DONE]").doesNotContain("prompt_tokens");
		}
	}

	@Test
	void openAiUsageChunkKeptWhenClientAskedForIt() {
		SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, false, JSON, 100_000);
		assertThat(run(sse, OPENAI_STREAM, 5)).isEqualTo(OPENAI_STREAM);
	}

	@Test
	void anthropicUsageIncludesCacheTokens() {
		SseProcessor sse = new SseProcessor(Endpoint.MESSAGES, false, JSON, 100_000);
		String out = run(sse, ANTHROPIC_STREAM, 3);
		assertThat(out).isEqualTo(ANTHROPIC_STREAM);
		assertThat(sse.usage()).isEqualTo(new Usage(20, 42, 100, 1000));
		assertThat(sse.usage().totalInput()).isEqualTo(1120);
	}

	@Test
	void handlesCrLfLineEndings() {
		String crlf = OPENAI_STREAM.replace("\n", "\r\n");
		SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, true, JSON, 100_000);
		String out = run(sse, crlf, 4);
		assertThat(sse.usage().input()).isEqualTo(11);
		assertThat(out).contains("[DONE]").doesNotContain("prompt_tokens");
	}

	@Test
	void multiByteCharactersSplitAcrossChunksSurvive() {
		String stream = "data: {\"choices\":[{\"delta\":{\"content\":\"你好🎉\"}}]}\n\n";
		SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, true, JSON, 100_000);
		assertThat(run(sse, stream, 1)).isEqualTo(stream);
	}

	@Test
	void responsesApiUsageComesFromTheCompletedEvent() {
		String stream = """
				event: response.created
				data: {"type":"response.created","response":{"id":"r1","status":"in_progress","usage":null}}

				event: response.output_text.delta
				data: {"type":"response.output_text.delta","delta":"Hi"}

				event: response.completed
				data: {"type":"response.completed","response":{"id":"r1","usage":{"input_tokens":70,"output_tokens":9,"total_tokens":79}}}

				""";
		SseProcessor sse = new SseProcessor(Endpoint.RESPONSES, false, JSON, 100_000);
		assertThat(run(sse, stream, 9)).isEqualTo(stream);
		assertThat(sse.usage()).isEqualTo(new Usage(70, 9, 0, 0));
	}

	@Test
	void commentLinesAndEventsWithoutDataPassThrough() {
		String stream = ": keep-alive\n\nevent: ping\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n";
		SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, true, JSON, 100_000);
		assertThat(run(sse, stream, 2)).isEqualTo(stream);
	}

	@Test
	void capturedTextIsLimited() {
		SseProcessor sse = new SseProcessor(Endpoint.CHAT_COMPLETIONS, true, JSON, 50);
		run(sse, OPENAI_STREAM, 10);
		assertThat(sse.capturedText()).hasSize(50);
	}

}
