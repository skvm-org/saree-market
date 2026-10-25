package com.sareemarket.reference;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.reference.dto.AdminRefDto;
import com.sareemarket.reference.dto.RefRequest;

import jakarta.validation.Valid;

// ADMIN-only: SecurityConfig guards everything under /api/v1/admin.
@RestController
@RequestMapping("/api/v1/admin")
class ReferenceAdminController {

	private final ReferenceAdminService service;

	ReferenceAdminController(ReferenceAdminService service) {
		this.service = service;
	}

	@GetMapping("/saree-types")
	List<AdminRefDto> allTypes() {
		return service.allTypes();
	}

	@GetMapping("/fabrics")
	List<AdminRefDto> allFabrics() {
		return service.allFabrics();
	}

	@PostMapping("/saree-types")
	@ResponseStatus(HttpStatus.CREATED)
	AdminRefDto createType(@Valid @RequestBody RefRequest request) {
		return service.createType(request);
	}

	@PutMapping("/saree-types/{id}")
	AdminRefDto updateType(@PathVariable long id, @Valid @RequestBody RefRequest request) {
		return service.updateType(id, request);
	}

	@PostMapping("/fabrics")
	@ResponseStatus(HttpStatus.CREATED)
	AdminRefDto createFabric(@Valid @RequestBody RefRequest request) {
		return service.createFabric(request);
	}

	@PutMapping("/fabrics/{id}")
	AdminRefDto updateFabric(@PathVariable long id, @Valid @RequestBody RefRequest request) {
		return service.updateFabric(id, request);
	}

}
