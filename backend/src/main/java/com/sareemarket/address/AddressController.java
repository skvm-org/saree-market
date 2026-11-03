package com.sareemarket.address;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.address.dto.AddressDto;
import com.sareemarket.address.dto.AddressRequest;

import jakarta.validation.Valid;

/**
 * A customer's saved delivery addresses, under /api/v1/addresses. CUSTOMER-only (class-level @PreAuthorize): sellers
 * and admins get 403 FORBIDDEN, anonymous callers 401 UNAUTHORIZED. Every call works on the caller's own addresses;
 * another customer's address id answers 404, never its contents.
 */
@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
class AddressController {

	private final AddressService service;

	AddressController(AddressService service) {
		this.service = service;
	}

	/** GET /api/v1/addresses. Lists the caller's addresses, default first, then oldest first. */
	@GetMapping
	List<AddressDto> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(userId(jwt));
	}

	/**
	 * POST /api/v1/addresses. Saves a new address. The first one becomes the default automatically.
	 *
	 * @return 201 with the saved address
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR; 409 ADDRESS_LIMIT (already 10 saved)
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AddressDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
		return service.create(userId(jwt), request);
	}

	/**
	 * PUT /api/v1/addresses/{id}. Replaces all fields of an address. makeDefault true moves the default here; false or
	 * absent leaves the default alone (it cannot be used to unset the default).
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR; 404 NOT_FOUND (missing or not the caller's)
	 */
	@PutMapping("/{id}")
	AddressDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody AddressRequest request) {
		return service.update(userId(jwt), id, request);
	}

	/**
	 * DELETE /api/v1/addresses/{id}. Removes an address. Returns 204. If it was the default, the newest remaining address
	 * becomes the default.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND (missing or not the caller's)
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.delete(userId(jwt), id);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
