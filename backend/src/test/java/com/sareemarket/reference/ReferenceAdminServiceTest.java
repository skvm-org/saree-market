package com.sareemarket.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import com.sareemarket.common.ApiException;
import com.sareemarket.reference.dto.AdminRefDto;
import com.sareemarket.reference.dto.RefRequest;

class ReferenceAdminServiceTest {

	SareeTypeRepository types = mock(SareeTypeRepository.class);

	FabricRepository fabrics = mock(FabricRepository.class);

	ReferenceAdminService service;

	@BeforeEach
	void setUp() {
		service = new ReferenceAdminService(types, fabrics);
		when(types.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
		when(fabrics.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
	}

	@Test
	void createTypeTrimsNameAndStartsActive() {
		AdminRefDto dto = service.createType(new RefRequest("  Paithani ", null));

		assertThat(dto.name()).isEqualTo("Paithani");
		assertThat(dto.active()).isTrue();
	}

	@Test
	void createFabricCanStartInactive() {
		assertThat(service.createFabric(new RefRequest("Crepe", false)).active()).isFalse();
	}

	@Test
	void createRejectsNameAlreadyUsedIgnoringCase() {
		when(types.existsByNameIgnoreCaseAndIdNot("kanjivaram", 0L)).thenReturn(true);

		assertThatThrownBy(() -> service.createType(new RefRequest("kanjivaram", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
				assertThat(e.code()).isEqualTo("NAME_TAKEN");
			});
		verify(types, never()).saveAndFlush(any());
	}

	@Test
	void createMapsARacingDuplicateToConflict() {
		when(fabrics.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

		assertThatThrownBy(() -> service.createFabric(new RefRequest("Silk", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("NAME_TAKEN"));
	}

	@Test
	void updateTypeRenamesAndKeepsActiveWhenOmitted() {
		SareeType type = new SareeType("Old");
		when(types.findById(5L)).thenReturn(Optional.of(type));

		AdminRefDto dto = service.updateType(5, new RefRequest("New", null));

		assertThat(dto.name()).isEqualTo("New");
		assertThat(dto.active()).isTrue();
	}

	@Test
	void updateFabricCanDeactivate() {
		Fabric fabric = new Fabric("Silk");
		when(fabrics.findById(5L)).thenReturn(Optional.of(fabric));

		assertThat(service.updateFabric(5, new RefRequest("Silk", false)).active()).isFalse();
	}

	@Test
	void updateChecksTheNameAgainstOtherEntriesOnly() {
		when(fabrics.findById(5L)).thenReturn(Optional.of(new Fabric("Silk")));
		when(fabrics.existsByNameIgnoreCaseAndIdNot("Cotton", 5L)).thenReturn(true);

		assertThatThrownBy(() -> service.updateFabric(5, new RefRequest("Cotton", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
	}

	@Test
	void updateUnknownIdIsNotFound() {
		when(types.findById(anyLong())).thenReturn(Optional.empty());
		when(fabrics.findById(anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.updateType(9, new RefRequest("X", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
		assertThatThrownBy(() -> service.updateFabric(9, new RefRequest("X", null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
		verify(types, never()).existsByNameIgnoreCaseAndIdNot(anyString(), anyLong());
	}

}
