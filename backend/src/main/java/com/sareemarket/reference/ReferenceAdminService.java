package com.sareemarket.reference;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.common.ApiException;
import com.sareemarket.reference.dto.AdminRefDto;
import com.sareemarket.reference.dto.RefRequest;

@Service
class ReferenceAdminService {

	private static final long NO_ID = 0L;

	private final SareeTypeRepository types;

	private final FabricRepository fabrics;

	ReferenceAdminService(SareeTypeRepository types, FabricRepository fabrics) {
		this.types = types;
		this.fabrics = fabrics;
	}

	/** Every entry, including the deactivated ones that /reference/* hides. */
	@Transactional(readOnly = true)
	List<AdminRefDto> allTypes() {
		return types.findAll(Sort.by("name")).stream().map(t -> new AdminRefDto(t.getId(), t.getName(), t.isActive())).toList();
	}

	@Transactional(readOnly = true)
	List<AdminRefDto> allFabrics() {
		return fabrics.findAll(Sort.by("name")).stream().map(f -> new AdminRefDto(f.getId(), f.getName(), f.isActive())).toList();
	}

	@Transactional
	AdminRefDto createType(RefRequest request) {
		String name = request.name().strip();
		requireFree(types.existsByNameIgnoreCaseAndIdNot(name, NO_ID));
		SareeType type = new SareeType(name);
		if (request.active() != null) {
			type.setActive(request.active());
		}
		return save(types, type);
	}

	@Transactional
	AdminRefDto updateType(long id, RefRequest request) {
		SareeType type = types.findById(id).orElseThrow(() -> notFound("Saree type"));
		String name = request.name().strip();
		requireFree(types.existsByNameIgnoreCaseAndIdNot(name, id));
		type.setName(name);
		if (request.active() != null) {
			type.setActive(request.active());
		}
		return save(types, type);
	}

	@Transactional
	AdminRefDto createFabric(RefRequest request) {
		String name = request.name().strip();
		requireFree(fabrics.existsByNameIgnoreCaseAndIdNot(name, NO_ID));
		Fabric fabric = new Fabric(name);
		if (request.active() != null) {
			fabric.setActive(request.active());
		}
		return save(fabrics, fabric);
	}

	@Transactional
	AdminRefDto updateFabric(long id, RefRequest request) {
		Fabric fabric = fabrics.findById(id).orElseThrow(() -> notFound("Fabric"));
		String name = request.name().strip();
		requireFree(fabrics.existsByNameIgnoreCaseAndIdNot(name, id));
		fabric.setName(name);
		if (request.active() != null) {
			fabric.setActive(request.active());
		}
		return save(fabrics, fabric);
	}

	private static AdminRefDto save(SareeTypeRepository repository, SareeType type) {
		try {
			SareeType saved = repository.saveAndFlush(type);
			return new AdminRefDto(saved.getId(), saved.getName(), saved.isActive());
		}
		catch (DataIntegrityViolationException e) {
			throw duplicate(); // a concurrent request took the name after the check
		}
	}

	private static AdminRefDto save(FabricRepository repository, Fabric fabric) {
		try {
			Fabric saved = repository.saveAndFlush(fabric);
			return new AdminRefDto(saved.getId(), saved.getName(), saved.isActive());
		}
		catch (DataIntegrityViolationException e) {
			throw duplicate();
		}
	}

	private static void requireFree(boolean taken) {
		if (taken) {
			throw duplicate();
		}
	}

	private static ApiException duplicate() {
		return new ApiException(HttpStatus.CONFLICT, "NAME_TAKEN", "An entry with this name already exists");
	}

	private static ApiException notFound(String what) {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", what + " not found");
	}

}
