package com.sareemarket.catalog;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.catalog.dto.CatalogCollectionDto;
import com.sareemarket.catalog.dto.CatalogItemDto;
import com.sareemarket.catalog.dto.ItemDetailDto;
import com.sareemarket.common.PageDto;

import jakarta.validation.Valid;

/**
 * Read endpoints for browsing: the photo grid, the collection list, image search and the single-photo page.
 * Each method passes the filter and the caller's id (and role) from the JWT to CatalogService.
 */
@RestController
@RequestMapping("/api/v1")
class CatalogController {

	private final CatalogService service;

	CatalogController(CatalogService service) {
		this.service = service;
	}

	/**
	 * GET /api/v1/catalog. The photo grid: one card per saree that passes the filters, 24 per page.
	 * Callable by CUSTOMER and ADMIN.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when a filter value is out of range
	 */
	@GetMapping("/catalog")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	PageDto<CatalogItemDto> search(@AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute CatalogFilter filter) {
		return service.search(filter, Long.parseLong(jwt.getSubject()));
	}

	/**
	 * GET /api/v1/catalog/collections. The same filters as GET /catalog, but the results are whole collections, as
	 * the sellers posted them. Callable by CUSTOMER and ADMIN.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when a filter value is out of range
	 */
	@GetMapping("/catalog/collections")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	PageDto<CatalogCollectionDto> collections(@AuthenticationPrincipal Jwt jwt,
			@Valid @ModelAttribute CatalogFilter filter) {
		return service.searchCollections(filter, Long.parseLong(jwt.getSubject()));
	}

	/**
	 * POST /api/v1/catalog/image-search. Multipart: the "image" part plus the same optional filters as GET /catalog
	 * (as form fields or in the URL). Returns photos that look like the picture, closest first. Callable by CUSTOMER
	 * and ADMIN.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when the image part is missing or empty or a
	 *	filter value is out of range; the image processor rejects files that are not readable pictures
	 */
	@PostMapping(path = "/catalog/image-search", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
	PageDto<CatalogItemDto> imageSearch(@AuthenticationPrincipal Jwt jwt,
			@RequestParam(name = "image", required = false) MultipartFile image,
			@Valid @ModelAttribute CatalogFilter filter) {
		return service.imageSearch(image, filter, Long.parseLong(jwt.getSubject()));
	}

	/**
	 * GET /api/v1/items/{id}. One saree with its collection text and seller details.
	 * Callable by CUSTOMER, ADMIN and SELLER; a seller may only open their own photos.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND when the photo does not exist or its seller is disabled
	 * 	(admins and the owning seller still see it); 403 FORBIDDEN when a seller asks for another seller's photo
	 */
	@GetMapping("/items/{id}")
	@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'SELLER')")
	ItemDetailDto item(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.itemDetail(id, Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("role"));
	}

}
