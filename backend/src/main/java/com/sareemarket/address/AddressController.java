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

@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("hasRole('CUSTOMER')")
class AddressController {

	private final AddressService service;

	AddressController(AddressService service) {
		this.service = service;
	}

	@GetMapping
	List<AddressDto> list(@AuthenticationPrincipal Jwt jwt) {
		return service.list(userId(jwt));
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AddressDto create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
		return service.create(userId(jwt), request);
	}

	@PutMapping("/{id}")
	AddressDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @Valid @RequestBody AddressRequest request) {
		return service.update(userId(jwt), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.delete(userId(jwt), id);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
