package com.tokenfest.gateway.keys;

import java.util.List;

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
@RequestMapping("/api/admin/keys")
public class KeyAdminController {

	private final ApiKeyService service;

	public KeyAdminController(ApiKeyService service) {
		this.service = service;
	}

	@GetMapping
	public List<KeyResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public KeyResponse.Created create(@Valid @RequestBody KeyRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public KeyResponse update(@PathVariable Long id, @Valid @RequestBody KeyRequest request) {
		return service.update(id, request);
	}

	/** New secret for the same key; the old one stops working. Shown once. */
	@PostMapping("/{id}/regenerate")
	public KeyResponse.Created regenerate(@PathVariable Long id) {
		return service.regenerate(id);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(id);
	}

}
