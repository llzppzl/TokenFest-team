package com.tokenfest.gateway.settings;

import java.util.List;
import java.util.Map;

import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;

class SettingsTests extends IntegrationTestSupport {

	@Test
	void defaultsThenUpdate() {
		admin.get()
			.uri("/api/admin/settings")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.thresholds")
			.isEqualTo(List.of(50, 80, 100))
			.jsonPath("$.webhookUrl")
			.isEqualTo("")
			.jsonPath("$.emailConfigured")
			.isEqualTo(false);

		admin.put()
			.uri("/api/admin/settings")
			.bodyValue(Map.of("thresholds", List.of(90, 75, 90), "webhookUrl", " https://hooks.example.com/x ",
					"emails", List.of("a@b.co", "")))
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.thresholds")
			.isEqualTo(List.of(75, 90))
			.jsonPath("$.webhookUrl")
			.isEqualTo("https://hooks.example.com/x")
			.jsonPath("$.emails")
			.isEqualTo(List.of("a@b.co"));

		admin.get()
			.uri("/api/admin/settings")
			.exchange()
			.expectBody()
			.jsonPath("$.thresholds")
			.isEqualTo(List.of(75, 90));
	}

	@Test
	void invalidValuesAreRejected() {
		admin.put()
			.uri("/api/admin/settings")
			.bodyValue(Map.of("thresholds", List.of(0), "webhookUrl", "", "emails", List.of()))
			.exchange()
			.expectStatus()
			.isBadRequest();
		admin.put()
			.uri("/api/admin/settings")
			.bodyValue(Map.of("thresholds", List.of(50), "webhookUrl", "ftp://x", "emails", List.of()))
			.exchange()
			.expectStatus()
			.isBadRequest();
		admin.put()
			.uri("/api/admin/settings")
			.bodyValue(Map.of("thresholds", List.of(50), "webhookUrl", "", "emails", List.of("not-an-email")))
			.exchange()
			.expectStatus()
			.isBadRequest()
			.expectBody()
			.jsonPath("$.error")
			.isEqualTo("Not a valid email address: not-an-email");
	}

}
