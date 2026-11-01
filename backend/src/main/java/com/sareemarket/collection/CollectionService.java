package com.sareemarket.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.collection.dto.CollectionDetailDto;
import com.sareemarket.collection.dto.CollectionSummaryDto;
import com.sareemarket.collection.dto.CreateCollectionForm;
import com.sareemarket.collection.dto.UpdateCollectionRequest;
import com.sareemarket.common.ApiException;
import com.sareemarket.item.ItemService;
import com.sareemarket.item.dto.ItemDto;
import com.sareemarket.item.ItemStatus;
import com.sareemarket.item.ProcessedImage;
import com.sareemarket.notification.event.CollectionPublished;
import com.sareemarket.notification.event.ItemsAdded;

@Service
public class CollectionService {

	private final SareeCollectionRepository collections;

	private final ItemService items;

	private final TransactionTemplate tx;

	private final ApplicationEventPublisher events;

	CollectionService(SareeCollectionRepository collections,
			ItemService items, TransactionTemplate tx, ApplicationEventPublisher events) {
		this.events = events;
		this.collections = collections;
		this.items = items;
		this.tx = tx;
	}

	public CollectionDetailDto create(long sellerId, CreateCollectionForm form) {
		List<ProcessedImage> images = items.process(form.files());
		int quantity = form.quantity() == null ? 1 : form.quantity();
		Long id = saveWithPhotos(sellerId, images, quantity, () -> collections.save(new SareeCollection(sellerId,
				form.title().strip(), form.price(), blankToNull(form.description()))),
				collectionId -> new CollectionPublished(sellerId, collectionId, images.size()));
		return detail(id);
	}

	public CollectionDetailDto addPhotos(long sellerId, long collectionId, List<MultipartFile> files, int quantity) {
		SareeCollection collection = owned(collectionId, sellerId);
		List<ProcessedImage> images = items.process(files);
		saveWithPhotos(sellerId, images, quantity, () -> collection,
				id -> new ItemsAdded(sellerId, id, images.size()));
		return detail(collectionId);
	}

	/** Reseller, admin or owning seller. */
	public CollectionDetailDto get(long id, long userId, boolean isSeller) {
		SareeCollection collection = find(id);
		if (isSeller && !collection.getSellerId().equals(userId)) {
			throw forbidden();
		}
		return detail(collection);
	}

	public List<CollectionSummaryDto> listMine(long sellerId) {
		List<CollectionSummaryDto> summaries = collections.summariesFor(sellerId);
		if (summaries.isEmpty()) {
			return summaries;
		}
		Map<Long, List<Long>> previews = new HashMap<>();
		for (Object[] row : collections.previewItems(summaries.stream().map(CollectionSummaryDto::id).toList())) {
			previews.computeIfAbsent(((Number) row[0]).longValue(), k -> new ArrayList<>()).add(((Number) row[1]).longValue());
		}
		return summaries.stream().map(s -> s.withPreviews(previews.getOrDefault(s.id(), List.of()))).toList();
	}

	public CollectionDetailDto update(long sellerId, long id, UpdateCollectionRequest request) {
		SareeCollection collection = owned(id, sellerId);
		collection.update(request.title().strip(), request.price(), blankToNull(request.description()));
		return detail(collections.save(collection));
	}

	public CollectionDetailDto setAllStatus(long sellerId, long id, ItemStatus status) {
		SareeCollection collection = owned(id, sellerId);
		items.setStatusForCollection(sellerId, id, status);
		return detail(collection);
	}

	public void delete(long sellerId, long id) {
		SareeCollection collection = owned(id, sellerId);
		List<String> files = items.fileKeysOf(id);
		collections.delete(collection); // the database removes the item rows with it
		items.deleteFiles(files);
	}

	/**
	 * Saves the collection row and its photos together. If anything fails, the rows roll back and the files
	 * already written are removed, so a failed upload leaves nothing behind.
	 */
	private Long saveWithPhotos(long sellerId, List<ProcessedImage> images, int quantity,
			java.util.function.Supplier<SareeCollection> collection, java.util.function.LongFunction<Object> event) {
		List<String> storedKeys = new ArrayList<>();
		try {
			return tx.execute(status -> {
				Long id = collection.get().getId();
				items.store(id, sellerId, images, quantity, storedKeys);
				// Delivered to followers only if this transaction commits.
				events.publishEvent(event.apply(id));
				return id;
			});
		}
		catch (RuntimeException e) {
			items.deleteFiles(storedKeys);
			throw e;
		}
	}

	private CollectionDetailDto detail(Long id) {
		return detail(find(id));
	}

	private CollectionDetailDto detail(SareeCollection c) {
		List<ItemDto> photos = items.dtosFor(c.getId(), c.getPrice());
		return new CollectionDetailDto(c.getId(), c.getSellerId(), c.getTitle(), c.getPrice(),
				c.getDescription(), c.getCreatedAt(), c.getUpdatedAt(), photos);
	}

	private SareeCollection find(long id) {
		return collections.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Collection not found"));
	}

	private SareeCollection owned(long id, long sellerId) {
		SareeCollection collection = find(id);
		if (!collection.getSellerId().equals(sellerId)) {
			throw forbidden();
		}
		return collection;
	}

	private static ApiException forbidden() {
		return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This collection belongs to another seller");
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
