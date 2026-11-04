package com.tokenfest.gateway.stats;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.tokenfest.gateway.export.CsvExporter;
import com.tokenfest.gateway.stats.StatsDtos.DailyRow;
import com.tokenfest.gateway.stats.StatsDtos.Page;
import com.tokenfest.gateway.stats.StatsDtos.RequestDetail;
import com.tokenfest.gateway.stats.StatsDtos.RequestRow;
import com.tokenfest.gateway.stats.StatsDtos.Summary;
import com.tokenfest.gateway.stats.StatsService.GroupBy;
import com.tokenfest.gateway.stats.StatsService.RequestFilter;
import com.tokenfest.gateway.web.ApiException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class StatsAdminController {

	private final StatsService stats;

	private final CsvExporter exporter;

	private final Clock clock;

	public StatsAdminController(StatsService stats, CsvExporter exporter, Clock clock) {
		this.stats = stats;
		this.exporter = exporter;
		this.clock = clock;
	}

	@GetMapping("/stats/summary")
	public Summary summary(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) Integer days) {
		return stats.summary(stats.range(from, to, days), null);
	}

	@GetMapping("/stats/daily")
	public List<DailyRow> daily(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) Integer days,
			@RequestParam(defaultValue = "key") String groupBy) {
		return stats.daily(stats.range(from, to, days), parseGroupBy(groupBy), null);
	}

	@GetMapping("/requests")
	public Page<RequestRow> requests(@RequestParam(required = false) LocalDate from,
			@RequestParam(required = false) LocalDate to, @RequestParam(required = false) Integer days,
			@RequestParam(required = false) Long keyId,
			@RequestParam(required = false) Long providerId, @RequestParam(required = false) String model,
			@RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return stats.requests(stats.range(from, to, days), new RequestFilter(keyId, providerId, model, status), page, size);
	}

	@GetMapping("/requests/{id}")
	public RequestDetail request(@PathVariable Long id) {
		return stats.request(id, null);
	}

	/** CSV of one month for all keys. */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@RequestParam(required = false) String month) {
		return exporter.export(CsvExporter.parseMonth(month, YearMonth.now(clock)), null);
	}

	static GroupBy parseGroupBy(String value) {
		try {
			return GroupBy.valueOf(value.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			throw ApiException.badRequest("groupBy must be key, model or provider");
		}
	}

}
