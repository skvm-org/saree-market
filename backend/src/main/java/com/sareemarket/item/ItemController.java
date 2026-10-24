package com.sareemarket.item;

import java.util.concurrent.TimeUnit;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sareemarket.item.dto.ItemDto;

import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1")
class ItemController {

	private final ItemService service;

	ItemController(ItemService service) {
		this.service = service;
	}

	@PatchMapping("/items/{id}")
	@PreAuthorize("hasRole('SELLER')")
	ItemDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody JsonNode patch) {
		return service.update(Long.parseLong(jwt.getSubject()), id, patch);
	}

	@DeleteMapping("/items/{id}")
	@PreAuthorize("hasRole('SELLER')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
		service.delete(Long.parseLong(jwt.getSubject()), id);
	}

	@GetMapping("/images/{itemId}/full")
	ResponseEntity<Resource> full(@PathVariable long itemId) {
		return image(itemId, false);
	}

	@GetMapping("/images/{itemId}/thumb")
	ResponseEntity<Resource> thumb(@PathVariable long itemId) {
		return image(itemId, true);
	}

	// A photo never changes once uploaded, so apps may cache it (private: it needs a login).
	private ResponseEntity<Resource> image(long itemId, boolean thumb) {
		return ResponseEntity.ok()
			.cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate().immutable())
			.contentType(MediaType.IMAGE_JPEG)
			.body(service.image(itemId, thumb));
	}

}
