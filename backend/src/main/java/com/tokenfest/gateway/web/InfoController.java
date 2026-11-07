package com.tokenfest.gateway.web;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Basic service info for the dashboard. The real admin API is added in later weeks.
 */
@RestController
@RequestMapping("/api")
public class InfoController {

	private final String version;

	public InfoController(@Value("${tokenfest.version}") String version) {
		this.version = version;
	}

	@GetMapping("/info")
	public Map<String, String> info() {
		return Map.of("name", "TokenFest", "version", version);
	}

}
