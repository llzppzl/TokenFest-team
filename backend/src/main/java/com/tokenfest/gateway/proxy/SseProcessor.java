package com.tokenfest.gateway.proxy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.tokenfest.gateway.usage.Usage;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;

/**
 * Passes a Server-Sent Events stream through to the client one complete event at a time,
 * and reads token usage from the events on the way.
 *
 * <p>
 * For OpenAI Chat Completions streams the gateway asks for usage
 * ({@code stream_options.include_usage}). If the client did not ask for it, the extra
 * usage-only chunk is removed so clients that expect {@code choices[0]} in every chunk
 * keep working.
 *
 * <p>
 * The amount of generated text is counted too, so usage can be estimated when a stream
 * ends before the provider reports it (for example when the client disconnects).
 *
 * <p>
 * One instance per response. Methods are synchronized because a cancel can arrive on a
 * different thread than the data.
 */
final class SseProcessor {

	/** An "event" longer than this without a blank line is passed on as it is. */
	static final int MAX_PENDING_BYTES = 1024 * 1024;

	private final Endpoint endpoint;

	private final boolean dropUsageOnlyChunks;

	private final JsonMapper json;

	private final int maxCapturedChars;

	/** Bytes received but not yet sent: {@code buf[0..len)}. */
	private byte[] buf = new byte[8192];

	private int len;

	/** Where to continue looking for the end of the current event. */
	private int scanFrom;

	private final StringBuilder captured = new StringBuilder();

	private Usage usage = Usage.ZERO;

	private boolean finalUsageSeen;

	private long outputChars;

	SseProcessor(Endpoint endpoint, boolean dropUsageOnlyChunks, JsonMapper json, int maxCapturedChars) {
		this.endpoint = endpoint;
		this.dropUsageOnlyChunks = dropUsageOnlyChunks;
		this.json = json;
		this.maxCapturedChars = maxCapturedChars;
	}

	Flux<DataBuffer> process(Flux<DataBuffer> upstream, DataBufferFactory factory) {
		// Copy and release each network buffer right away, so nothing pooled waits in a queue
		return upstream.map(SseProcessor::copyAndRelease)
			.concatMapIterable(this::onBytes)
			.concatWith(Flux.defer(() -> Flux.fromIterable(finish())))
			.map(factory::wrap);
	}

	synchronized Usage usage() {
		return usage;
	}

	/** True if the provider sent its final usage numbers. */
	synchronized boolean finalUsageSeen() {
		return finalUsageSeen;
	}

	/** Characters of generated text (and thinking/tool input) seen so far. */
	synchronized long outputChars() {
		return outputChars;
	}

	synchronized String capturedText() {
		return captured.toString();
	}

	/** Returns the complete events (as bytes) that should be sent to the client. */
	synchronized List<byte[]> onBytes(byte[] chunk) {
		append(chunk);
		List<byte[]> out = new ArrayList<>();
		int start = 0;
		int i = scanFrom;
		while (i < len) {
			int end = eventEnd(i);
			if (end == -2) {
				break; // need more bytes to know
			}
			if (end > 0) {
				emit(Arrays.copyOfRange(buf, start, end), out);
				start = end;
				i = end;
			}
			else {
				i++;
			}
		}
		scanFrom = i;
		compact(start);
		if (len > MAX_PENDING_BYTES) {
			// No event boundary in sight: pass the data on rather than hold it all in memory
			byte[] raw = Arrays.copyOf(buf, len);
			capture(new String(raw, StandardCharsets.UTF_8));
			out.add(raw);
			len = 0;
			scanFrom = 0;
		}
		return out;
	}

	/** Sends whatever is left when the upstream stream ends. */
	synchronized List<byte[]> finish() {
		byte[] rest = Arrays.copyOf(buf, len);
		len = 0;
		scanFrom = 0;
		List<byte[]> out = new ArrayList<>();
		if (rest.length > 0) {
			emit(rest, out);
		}
		return out;
	}

	/**
	 * If position {@code i} starts a blank line ({@code \n\n}, {@code \r\n\r\n} or
	 * {@code \r\r}), the index after it; -2 if more bytes are needed to tell; else -1.
	 */
	private int eventEnd(int i) {
		byte b = buf[i];
		if (b == '\n') {
			if (i + 1 >= len) {
				return -2;
			}
			if (buf[i + 1] == '\n') {
				return i + 2;
			}
			if (buf[i + 1] == '\r') {
				if (i + 2 >= len) {
					return -2;
				}
				return buf[i + 2] == '\n' ? i + 3 : -1;
			}
			return -1;
		}
		if (b == '\r') {
			if (i + 1 >= len) {
				return -2;
			}
			return buf[i + 1] == '\r' ? i + 2 : -1;
		}
		return -1;
	}

	private void append(byte[] chunk) {
		if (len + chunk.length > buf.length) {
			buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + chunk.length));
		}
		System.arraycopy(chunk, 0, buf, len, chunk.length);
		len += chunk.length;
	}

	private void compact(int start) {
		if (start == 0) {
			return;
		}
		System.arraycopy(buf, start, buf, 0, len - start);
		len -= start;
		scanFrom -= start;
	}

	private void emit(byte[] event, List<byte[]> out) {
		if (handleEvent(event)) {
			out.add(event);
		}
	}

	/** Reads usage from one event. Returns false if the event should not be sent. */
	private boolean handleEvent(byte[] eventBytes) {
		String event = new String(eventBytes, StandardCharsets.UTF_8);
		capture(event);
		StringBuilder data = new StringBuilder();
		for (String line : event.split("\r\n|\r|\n")) {
			if (line.startsWith("data:")) {
				if (!data.isEmpty()) {
					data.append('\n');
				}
				data.append(line.substring(5).stripLeading());
			}
		}
		if (data.isEmpty() || data.toString().equals("[DONE]")) {
			return true;
		}
		JsonNode node;
		try {
			node = json.readTree(data.toString());
		}
		catch (JacksonException e) {
			return true;
		}
		String type = node.path("type").asString("");
		switch (endpoint) {
			case MESSAGES -> {
				if (type.equals("message_start")) {
					usage = usage.max(Usage.fromAnthropic(node.path("message").path("usage")));
				}
				else if (type.equals("message_delta")) {
					usage = usage.max(Usage.fromAnthropic(node.path("usage")));
					finalUsageSeen = true;
				}
				else if (type.equals("content_block_delta")) {
					JsonNode delta = node.path("delta");
					outputChars += textLength(delta, "text") + textLength(delta, "thinking")
							+ textLength(delta, "partial_json");
				}
			}
			case RESPONSES -> {
				// response.completed (or .incomplete / .failed) carries the final usage
				JsonNode responseUsage = node.path("response").path("usage");
				if (type.startsWith("response.") && responseUsage.isObject()) {
					usage = Usage.fromResponses(responseUsage);
					finalUsageSeen = true;
				}
				else if (type.endsWith(".delta")) {
					outputChars += textLength(node, "delta");
				}
			}
			case CHAT_COMPLETIONS -> {
				for (JsonNode choice : node.path("choices")) {
					JsonNode delta = choice.path("delta");
					outputChars += textLength(delta, "content") + textLength(delta, "reasoning_content");
					for (JsonNode call : delta.path("tool_calls")) {
						outputChars += textLength(call.path("function"), "arguments");
					}
				}
				JsonNode usageNode = node.path("usage");
				if (usageNode.isObject()) {
					usage = Usage.fromOpenAi(usageNode);
					finalUsageSeen = true;
					JsonNode choices = node.path("choices");
					if (dropUsageOnlyChunks && choices.isArray() && choices.isEmpty()) {
						return false;
					}
				}
			}
			default -> {
				// no usage in these streams
			}
		}
		return true;
	}

	private static int textLength(JsonNode node, String field) {
		JsonNode value = node.path(field);
		return value.isString() ? value.stringValue().length() : 0;
	}

	private void capture(String event) {
		int room = maxCapturedChars - captured.length();
		if (room > 0) {
			captured.append(event, 0, Math.min(room, event.length()));
		}
	}

	private static byte[] copyAndRelease(DataBuffer buffer) {
		try {
			int length = buffer.readableByteCount();
			ByteBuffer target = ByteBuffer.allocate(length);
			buffer.toByteBuffer(buffer.readPosition(), target, 0, length);
			return target.array();
		}
		finally {
			DataBufferUtils.release(buffer);
		}
	}

}
