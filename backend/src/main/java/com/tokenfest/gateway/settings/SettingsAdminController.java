package com.tokenfest.gateway.settings;

import java.util.List;

import com.tokenfest.gateway.alerts.AlertNotifier;
import com.tokenfest.gateway.alerts.AlertNotifier.DeliveryResult;
import com.tokenfest.gateway.alerts.AlertService;
import com.tokenfest.gateway.alerts.AlertService.AlertEventResponse;
import com.tokenfest.gateway.settings.SettingsService.AlertSettings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class SettingsAdminController {

	/** Alert settings plus whether an SMTP server is configured. */
	public record SettingsResponse(List<Integer> thresholds, String webhookUrl, List<String> emails,
			boolean emailConfigured) {
	}

	private final SettingsService settings;

	private final AlertService alerts;

	private final AlertNotifier notifier;

	public SettingsAdminController(SettingsService settings, AlertService alerts, AlertNotifier notifier) {
		this.settings = settings;
		this.alerts = alerts;
		this.notifier = notifier;
	}

	@GetMapping("/settings")
	public SettingsResponse get() {
		return response(settings.alertSettings());
	}

	@PutMapping("/settings")
	public SettingsResponse update(@RequestBody AlertSettings request) {
		return response(settings.updateAlertSettings(request));
	}

	@PostMapping("/settings/test-alert")
	public DeliveryResult testAlert() {
		return alerts.sendTest();
	}

	@GetMapping("/alerts")
	public List<AlertEventResponse> recentAlerts() {
		return alerts.recent();
	}

	private SettingsResponse response(AlertSettings s) {
		return new SettingsResponse(s.thresholds(), s.webhookUrl(), s.emails(), notifier.emailConfigured());
	}

}
