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
import com.sareemarket.collection.dto.CollectionSummaryDto;
import com.sareemarket.collection.dto.CreateCollectionForm;
import com.sareemarket.collection.dto.UpdateCollectionRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
class CollectionController {

	private final CollectionService service;

	CollectionController(CollectionService service) {
		this.service = service;
	}

	@PostMapping(path = "/collections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.CREATED)
	CollectionDetailDto create(@AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute CreateCollectionForm form) {
		return service.create(userId(jwt), form);
	}

	@GetMapping("/collections/{id}")
	@PreAuthorize("hasAnyRole('SELLER', 'CUSTOMER', 'ADMIN')")
	CollectionDetailDto get(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		return service.get(id, userId(jwt), "SELLER".equals(jwt.getClaimAsString("role")));
	}

	@PutMapping("/collections/{id}")
	@PreAuthorize("hasRole('SELLER')")
	CollectionDetailDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody UpdateCollectionRequest request) {
		return service.update(userId(jwt), id, request);
	}

	@DeleteMapping("/collections/{id}")
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.delete(userId(jwt), id);
	}

	@PatchMapping("/collections/{id}/items/status")
	@PreAuthorize("hasRole('SELLER')")
	CollectionDetailDto setAllStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@Valid @RequestBody BulkStatusRequest request) {
		return service.setAllStatus(userId(jwt), id, request.status());
	}

	@GetMapping("/sellers/me/collections")
	@PreAuthorize("hasRole('SELLER')")
	List<CollectionSummaryDto> mine(@AuthenticationPrincipal Jwt jwt) {
		return service.listMine(userId(jwt));
	}

	@PostMapping(path = "/collections/{id}/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.CREATED)
	CollectionDetailDto addPhotos(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
			@RequestParam(name = "files", required = false) List<MultipartFile> files) {
		return service.addPhotos(userId(jwt), id, files);
	}

	private static long userId(Jwt jwt) {
		return Long.parseLong(jwt.getSubject());
	}

}
