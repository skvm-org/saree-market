package com.sareemarket.cart;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.common.ApiException;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("hasRole('CUSTOMER')")
class CartController {

	private final CartService service;

	CartController(CartService service) {
		this.service = service;
	}

	@PutMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setLine(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId, @RequestBody JsonNode body) {
		JsonNode q = body.get("quantity");
		if (q == null || !q.isIntegralNumber() || !q.canConvertToInt() || q.intValue() < 1 || q.intValue() > 10000) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					Map.of("quantity", "must be a whole number from 1 to 10000"));
		}
		service.setLine(Long.parseLong(jwt.getSubject()), itemId, q.intValue());
	}

	@DeleteMapping("/items/{itemId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void removeLine(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
		service.removeLine(Long.parseLong(jwt.getSubject()), itemId);
	}

}
