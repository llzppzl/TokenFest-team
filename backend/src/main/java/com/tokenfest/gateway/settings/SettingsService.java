package com.tokenfest.gateway.settings;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.tokenfest.gateway.domain.AppSetting;
import com.tokenfest.gateway.repository.AppSettingRepository;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings the admin can change from the dashboard, stored in {@code app_settings}.
 */
@Service
public class SettingsService {

	/**
	 * @param thresholds budget percentages that trigger an alert, sorted
	 * @param webhookUrl JSON POST target (for example a Slack incoming webhook), or empty
	 * @param emails alert email recipients
	 */
	public record AlertSettings(List<Integer> thresholds, String webhookUrl, List<String> emails) {
	}

	static final String THRESHOLDS = "alert.thresholds";

	static final String WEBHOOK_URL = "alert.webhookUrl";

	static final String EMAILS = "alert.emails";

	static final List<Integer> DEFAULT_THRESHOLDS = List.of(50, 80, 100);

	private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final AppSettingRepository settings;

	public SettingsService(AppSettingRepository settings) {
		this.settings = settings;
	}

	@Transactional(readOnly = true)
	public AlertSettings alertSettings() {
		Map<String, String> values = settings.findAll()
			.stream()
			.collect(Collectors.toMap(AppSetting::getSettingKey, AppSetting::getSettingValue));
		String thresholds = values.get(THRESHOLDS);
		return new AlertSettings(thresholds == null ? DEFAULT_THRESHOLDS : split(thresholds, Integer::valueOf),
				values.getOrDefault(WEBHOOK_URL, ""), split(values.getOrDefault(EMAILS, ""), Function.identity()));
	}

	@Transactional
	public AlertSettings updateAlertSettings(AlertSettings request) {
		List<Integer> thresholds = request.thresholds() == null ? List.of()
				: request.thresholds().stream().distinct().sorted().toList();
		if (thresholds.stream().anyMatch(t -> t == null || t < 1 || t > 1000)) {
			throw ApiException.badRequest("Thresholds must be between 1 and 1000 percent");
		}
		String webhook = request.webhookUrl() == null ? "" : request.webhookUrl().trim();
		if (!webhook.isEmpty() && !webhook.matches("https?://\\S+")) {
			throw ApiException.badRequest("Webhook URL must start with http:// or https://");
		}
		List<String> emails = request.emails() == null ? List.of()
				: request.emails().stream().map(String::trim).filter(e -> !e.isEmpty()).distinct().toList();
		for (String email : emails) {
			if (!EMAIL.matcher(email).matches()) {
				throw ApiException.badRequest("Not a valid email address: " + email);
			}
		}
		save(THRESHOLDS, thresholds.stream().map(String::valueOf).collect(Collectors.joining(",")));
		save(WEBHOOK_URL, webhook);
		save(EMAILS, String.join(",", emails));
		return new AlertSettings(thresholds, webhook, emails);
	}

	private void save(String key, String value) {
		AppSetting setting = settings.findById(key).orElseGet(() -> new AppSetting(key, value));
		setting.setSettingValue(value);
		settings.save(setting);
	}

	private static <T> List<T> split(String value, Function<String, T> parse) {
		return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(parse).toList();
	}

}
