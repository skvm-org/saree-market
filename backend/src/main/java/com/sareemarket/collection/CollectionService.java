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

/**
 * Collections: create, edit, list and delete the groups of sarees a seller posts. Called by CollectionController.
 * Owns the collection rows and drives the photos through ItemService. A seller may only change their own
 * collections. Publishes CollectionPublished and ItemsAdded (inside the transaction, delivered after commit) so
 * the notification feature can tell followers; this class never calls it directly.
 */
@Service
public class CollectionService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CollectionService.class);

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

	/**
	 * Creates a collection and its photos together. Photos are processed first, so one bad file saves nothing. The
	 * collection title and description are stripped; a blank description is stored as null. Publishes CollectionPublished.
	 *
	 * @param form the quantity is how many pieces of each saree the seller has (1 when absent)
	 * @throws ApiException 400 VALIDATION_ERROR (no photos or more than 30), 400 INVALID_IMAGE for a bad file
	 */
	public CollectionDetailDto create(long sellerId, CreateCollectionForm form) {
		List<ProcessedImage> images = items.process(form.files());
		int quantity = form.quantity() == null ? 1 : form.quantity();
		Long id = saveWithPhotos(sellerId, images, quantity, () -> collections.save(new SareeCollection(sellerId,
				form.title().strip(), form.price(), blankToNull(form.description()))),
				collectionId -> new CollectionPublished(sellerId, collectionId, images.size()));
		log.info("Collection {} created by seller {} with {} photos", id, sellerId, images.size());
		return detail(id);
	}

	/**
	 * Adds photos to an existing collection, all with the same quantity. Same all-or-nothing handling as create().
	 * Publishes ItemsAdded.
	 *
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when the collection is another seller's; 400 as for create()
	 */
	public CollectionDetailDto addPhotos(long sellerId, long collectionId, List<MultipartFile> files, int quantity) {
		SareeCollection collection = owned(collectionId, sellerId);
		List<ProcessedImage> images = items.process(files);
		saveWithPhotos(sellerId, images, quantity, () -> collection,
				id -> new ItemsAdded(sellerId, id, images.size()));
		return detail(collectionId);
	}

	/**
	 * One collection with all its photos. Any customer or admin may read any collection; a seller only their own.
	 *
	 * @param isSeller whether the caller's role is SELLER
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when a seller asks for another seller's collection
	 */
	public CollectionDetailDto get(long id, long userId, boolean isSeller) {
		SareeCollection collection = find(id);
		if (isSeller && !collection.getSellerId().equals(userId)) {
			throw forbidden();
		}
		return detail(collection);
	}

	/**
	 * The seller's dashboard: their collections newest first, each with item counts per status and up to three
	 * preview photos. Uses one query for the counts and one for all the previews, not one per collection.
	 */
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

	/**
	 * Changes the title, price and description. The price applies to every photo that has no price of its own. The
	 * database refreshes the search vector of the photos when the title or description changes.
	 *
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when the collection is another seller's
	 */
	public CollectionDetailDto update(long sellerId, long id, UpdateCollectionRequest request) {
		SareeCollection collection = owned(id, sellerId);
		collection.update(request.title().strip(), request.price(), blankToNull(request.description()));
		return detail(collections.save(collection));
	}

	/**
	 * Marks every photo in the collection AVAILABLE or SOLD_OUT in one go (the seller's "mark all sold out" button).
	 *
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when the collection is another seller's
	 */
	public CollectionDetailDto setAllStatus(long sellerId, long id, ItemStatus status) {
		SareeCollection collection = owned(id, sellerId);
		items.setStatusForCollection(sellerId, id, status);
		return detail(collection);
	}

	/**
	 * Deletes the collection. The database deletes the photo rows with it; the image files are removed from storage
	 * afterwards.
	 *
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when the collection is another seller's
	 */
	public void delete(long sellerId, long id) {
		SareeCollection collection = owned(id, sellerId);
		List<String> files = items.fileKeysOf(id);
		collections.delete(collection); // the database removes the item rows with it
		items.deleteFiles(files);
	}

	/**
	 * Saves the collection row and its photos together. If anything fails, the rows roll back and the files
	 * already written are removed, so a failed upload leaves nothing behind.
	 *
	 * @param collection saves or returns the collection row; runs inside the transaction
	 * @param event builds the event to publish from the collection id; the notification listeners receive it only
	 *	if the transaction commits
	 * @return the collection id
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

	/**
	 * Loads the collection and builds its detail response.
	 */
	private CollectionDetailDto detail(Long id) {
		return detail(find(id));
	}

	private CollectionDetailDto detail(SareeCollection c) {
		List<ItemDto> photos = items.dtosFor(c.getId(), c.getPrice());
		return new CollectionDetailDto(c.getId(), c.getSellerId(), c.getTitle(), c.getPrice(),
				c.getDescription(), c.getCreatedAt(), c.getUpdatedAt(), photos);
	}

	/**
	 * @throws ApiException 404 NOT_FOUND when there is no such collection
	 */
	private SareeCollection find(long id) {
		return collections.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Collection not found"));
	}

	/**
	 * The collection, if it belongs to this seller.
	 *
	 * @throws ApiException 404 NOT_FOUND; 403 FORBIDDEN when it belongs to another seller
	 */
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

	/**
	 * Trims the text; blank becomes null so an empty description is stored as no description.
	 */
	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
