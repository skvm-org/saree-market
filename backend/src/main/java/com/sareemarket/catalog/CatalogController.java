package com.sareemarket.catalog;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.catalog.dto.ItemDetailDto;
import com.sareemarket.common.PageDto;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class CatalogController {

	private final CatalogService service;

	CatalogController(CatalogService service) {
		this.service = service;
	}

	@GetMapping("/catalog")
	@PreAuthorize("hasAnyRole('RESELLER', 'ADMIN')")
	PageDto<CatalogItemDto> search(@Valid @ModelAttribute CatalogFilter filter) {
		return service.search(filter);
	}

	@GetMapping("/items/{id}")
	@PreAuthorize("hasAnyRole('RESELLER', 'ADMIN', 'SELLER')")
	ItemDetailDto item(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.itemDetail(id, Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("role"));
	}

}
