package com.sareemarket.collection;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.collection.dto.BulkStatusRequest;
import com.sareemarket.collection.dto.CollectionDetailDto;
import com.sareemarket.common.ApiException;
import com.sareemarket.collection.dto.CollectionSummaryDto;
import com.sareemarket.collection.dto.CreateCollectionForm;
import com.sareemarket.collection.dto.UpdateCollectionRequest;

import jakarta.validation.Valid;

/**
 * Seller endpoints for collections (a title, a price and a set of sarees' photos) plus the read endpoint that
 * customers also use. Ownership rules live in CollectionService; this class only parses the request and passes the
 * caller's id from the JWT. Per-photo changes (colour, price, quantity, status) are in the item feature.
 */
@RestController
@RequestMapping("/api/v1")
class CollectionController {

	private final CollectionService service;

	CollectionController(CollectionService service) {
		this.service = service;
	}

	/**
	 * POST /api/v1/collections. Creates a collection with its photos in one multipart request. SELLER only; answers 201.
	 * Followers are notified after it commits.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR for a bad field or a photo count outside 1 to 50;
	 * 	400 INVALID_IMAGE for a file that is not a readable picture
	 */
	@PostMapping(path = "/collections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.CREATED)
	CollectionDetailDto create(@AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute CreateCollectionForm form) {
		return service.create(userId(jwt), form);
	}

	/**
	 * GET /api/v1/collections/{id}. A collection with all its photos. Callable by SELLER (own collections only),
	 * CUSTOMER and ADMIN.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND for an unknown id; 403 FORBIDDEN when a seller asks for
	 * 	another seller's collection
	 */
	@GetMapping("/collections/{id}")
	@PreAuthorize("hasAnyRole('SELLER', 'CUSTOMER', 'ADMIN')")
	CollectionDetailDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.get(id, userId(jwt), "SELLER".equals(jwt.getClaimAsString("role")));
	}

	/**
	 * PUT /api/v1/collections/{id}. Replaces the title, price and description. SELLER only, own collections.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR for a bad field; 404 NOT_FOUND; 403 FORBIDDEN
	 * 	when the collection belongs to another seller
	 */
	@PutMapping("/collections/{id}")
	@PreAuthorize("hasRole('SELLER')")
	CollectionDetailDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody UpdateCollectionRequest request) {
		return service.update(userId(jwt), id, request);
	}

	/**
	 * DELETE /api/v1/collections/{id}. Removes the collection, its photos and their image files. SELLER only,
	 * own collections; answers 204.
	 *
	 * @throws com.sareemarket.common.ApiException 404 NOT_FOUND; 403 FORBIDDEN when it belongs to another seller
	 */
	@DeleteMapping("/collections/{id}")
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.delete(userId(jwt), id);
	}

	/**
	 * PATCH /api/v1/collections/{id}/items/status. Marks every photo in the collection AVAILABLE or SOLD_OUT at
	 * once. SELLER only, own collections.
	 *
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR when status is missing; 404 NOT_FOUND;
	 * 	403 FORBIDDEN when the collection belongs to another seller
	 */
	@PatchMapping("/collections/{id}/items/status")
	@PreAuthorize("hasRole('SELLER')")
	CollectionDetailDto setAllStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody BulkStatusRequest request) {
		return service.setAllStatus(userId(jwt), id, request.status());
	}

	/**
	 * GET /api/v1/sellers/me/collections. The logged-in seller's collections for the dashboard, newest first,
	 * with counts and up to three preview photos each. SELLER only.
	 */
	@GetMapping("/sellers/me/collections")
	@PreAuthorize("hasRole('SELLER')")
	List<CollectionSummaryDto> mine(@AuthenticationPrincipal Jwt jwt) {
		return service.listMine(userId(jwt));
	}

	/**
	 * POST /api/v1/collections/{id}/items. Adds more photos to a collection, each with the given quantity
	 * (default 1). SELLER only, own collections; answers 201. Followers are notified after it commits.
	 *
	 * @param quantity pieces of each new saree, as text so a bad value can be reported as a field error
	 * @throws com.sareemarket.common.ApiException 400 VALIDATION_ERROR for a bad quantity or a photo count outside
	 * 	1 to 50; 400 INVALID_IMAGE for an unreadable file; 404 NOT_FOUND; 403 FORBIDDEN when the collection
	 * 	belongs to another seller
	 */
	@PostMapping(path = "/collections/{id}/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.CREATED)
	CollectionDetailDto addPhotos(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "files", required = false) List<MultipartFile> files,
			@RequestParam(name = "quantity", required = false) String quantity) {
		return service.addPhotos(userId(jwt), id, files, parseQuantity(quantity));
	}

	/** Absent means 1; anything but a whole number from 0 to 10000 is a validation error. */
	private static int parseQuantity(String value) {
		if (value == null || value.isBlank()) {
			return 1;
		}
		try {
			int quantity = Integer.parseInt(value.strip());
			if (quantity >= 0 && quantity <= 10000) {
				return quantity;
			}
		}
		catch (NumberFormatException e) {
			// falls through to the error below
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
				java.util.Map.of("quantity", "must be a whole number from 0 to 10000"));
	}

	/**
	 * The logged-in user's id, which is the JWT subject.
	 */
	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
