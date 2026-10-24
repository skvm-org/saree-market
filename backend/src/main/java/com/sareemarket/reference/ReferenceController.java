package com.sareemarket.reference;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reference")
class ReferenceController {

	private final SareeTypeRepository types;

	private final FabricRepository fabrics;

	ReferenceController(SareeTypeRepository types, FabricRepository fabrics) {
		this.types = types;
		this.fabrics = fabrics;
	}

	@GetMapping("/saree-types")
	List<RefDto> sareeTypes() {
		return types.findByActiveTrueOrderByName().stream().map(t -> new RefDto(t.getId(), t.getName())).toList();
	}

	@GetMapping("/fabrics")
	List<RefDto> fabrics() {
		return fabrics.findByActiveTrueOrderByName().stream().map(f -> new RefDto(f.getId(), f.getName())).toList();
	}

}
