package com.sareemarket.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.sareemarket.collection.dto.CreateCollectionForm;
import com.sareemarket.common.ApiException;
import com.sareemarket.item.ItemService;
import com.sareemarket.item.ProcessedImage;
import com.sareemarket.reference.Fabric;
import com.sareemarket.reference.FabricRepository;
import com.sareemarket.reference.SareeType;
import com.sareemarket.reference.SareeTypeRepository;

class CollectionServiceTest {

	SareeCollectionRepository collections = mock(SareeCollectionRepository.class);

	SareeTypeRepository types = mock(SareeTypeRepository.class);

	FabricRepository fabrics = mock(FabricRepository.class);

	ItemService items = mock(ItemService.class);

	TransactionTemplate tx = mock(TransactionTemplate.class);

	CollectionService service = new CollectionService(collections, types, fabrics, items, tx);

	CreateCollectionForm form = new CreateCollectionForm("Diwali silks", 1L, 2L, 2500, null, List.of());

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		when(types.findById(1L)).thenReturn(Optional.of(mock(SareeType.class, inv -> inv.getMethod().getName().equals("isActive") ? true : null)));
		when(fabrics.findById(2L)).thenReturn(Optional.of(mock(Fabric.class, inv -> inv.getMethod().getName().equals("isActive") ? true : null)));
		// Run the transaction callback straight away, as the real template would.
		when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
		when(items.process(any())).thenReturn(List.of(new ProcessedImage(new byte[1], new byte[1], 7L)));
		when(collections.save(any())).thenAnswer(inv -> inv.getArgument(0, SareeCollection.class));
	}

	@Test
	void filesAlreadyWrittenAreRemovedWhenSavingFails() {
		doAnswer(inv -> {
			inv.<List<String>>getArgument(3).addAll(List.of("1/a.jpg", "1/a_thumb.jpg"));
			throw new IllegalStateException("database is down");
		}).when(items).store(any(), anyLong(), anyList(), anyList());

		assertThatThrownBy(() -> service.create(1L, form)).isInstanceOf(IllegalStateException.class);

		verify(items).deleteFiles(List.of("1/a.jpg", "1/a_thumb.jpg"));
	}

	@Test
	void unknownSareeTypeIsRejectedBeforeAnyPhotoIsProcessed() {
		when(types.findById(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.create(1L, form)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.fields()).containsKey("sareeTypeId"));
		verify(items, never()).process(any());
	}

	@Test
	void sellerCannotDeleteAnotherSellersCollection() {
		SareeCollection others = new SareeCollection(99L, "Theirs", 1L, 2L, 1000, null);
		when(collections.findById(5L)).thenReturn(Optional.of(others));

		assertThatThrownBy(() -> service.delete(1L, 5L)).isInstanceOfSatisfying(ApiException.class,
				e -> assertThat(e.status()).isEqualTo(HttpStatus.FORBIDDEN));
		verify(collections, never()).delete(any());
	}

}
