package com.tokenfest.gateway.alerts;

import java.util.List;

import com.tokenfest.gateway.settings.SettingsService;
import com.tokenfest.gateway.settings.SettingsService.AlertSettings;
import com.tokenfest.gateway.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class EmailAlertTests extends IntegrationTestSupport {

	@MockitoBean
	private JavaMailSender mailSender;

	@Autowired
	private SettingsService settings;

	@Test
	void testAlertIsEmailed() {
		settings.updateAlertSettings(new AlertSettings(List.of(80), "", List.of("lead@example.com", "pi@example.com")));

		admin.get()
			.uri("/api/admin/settings")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.emailConfigured")
			.isEqualTo(true);

		admin.post()
			.uri("/api/admin/settings/test-alert")
			.exchange()
			.expectStatus()
			.isOk()
			.expectBody()
			.jsonPath("$.email")
			.isEqualTo("sent")
			.jsonPath("$.webhook")
			.isEqualTo("not configured");

		ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(mail.capture());
		assertThat(mail.getValue().getTo()).containsExactly("lead@example.com", "pi@example.com");
		assertThat(mail.getValue().getSubject()).isEqualTo("TokenFest test alert");
	}

}
