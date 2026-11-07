package com.tokenfest.gateway.provider;

import java.util.List;

import com.tokenfest.gateway.proxy.UpstreamClient.ConnectionTest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers")
public class ProviderAdminController {

	private final ProviderService service;

	public ProviderAdminController(ProviderService service) {
		this.service = service;
	}

	@GetMapping
	public List<ProviderResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProviderResponse create(@Valid @RequestBody ProviderRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public ProviderResponse update(@PathVariable Long id, @Valid @RequestBody ProviderRequest request) {
		return service.update(id, request);
	}

	/** Checks the base URL and API key by listing the provider's models. */
	@PostMapping("/{id}/test")
	public ConnectionTest test(@PathVariable Long id) {
		return service.test(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
