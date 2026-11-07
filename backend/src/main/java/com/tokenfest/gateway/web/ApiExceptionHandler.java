package com.tokenfest.gateway.web;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Map<String, String>> handleApi(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
	}

	@ExceptionHandler(WebExchangeBindException.class)
	ResponseEntity<Map<String, String>> handleValidation(WebExchangeBindException e) {
		String message = e.getFieldErrors()
			.stream()
			.map(error -> error.getField() + " " + error.getDefaultMessage())
			.sorted()
			.collect(Collectors.joining(", "));
		return ResponseEntity.badRequest().body(Map.of("error", message));
	}

	@ExceptionHandler(ServerWebInputException.class)
	ResponseEntity<Map<String, String>> handleBadInput(ServerWebInputException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request body"));
	}

}
