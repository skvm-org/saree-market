package com.sareemarket.item;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.collection.SareeCollection;
import com.sareemarket.collection.SareeCollectionRepository;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.ImageStorage;
import com.sareemarket.item.dto.ItemDto;
import com.sareemarket.notification.event.ItemsRestocked;

import tools.jackson.databind.JsonNode;

/**
 * Everything about a collection's photos: checking and processing uploads, writing files and rows, seller edits,
 * deletes, and serving the image files. {@code CollectionService} calls it when a collection is created, shown,
 * deleted or marked sold out. Publishes {@link ItemsRestocked} when photos go back on sale.
 */
@Service
public class ItemService {

	static final int MAX_PHOTOS = 50;

	private static final int MAX_COLOUR = 50;

	private static final int MAX_QUANTITY = 10000;

	private static final Logger log = LoggerFactory.getLogger(ItemService.class);

	private final ItemRepository items;

	private final SareeCollectionRepository collections;

	private final ImageProcessor processor;

	private final ImageStorage storage;

	private final ApplicationEventPublisher events;

	ItemService(ItemRepository items, SareeCollectionRepository collections, ImageProcessor processor,
			ImageStorage storage, ApplicationEventPublisher events) {
		this.events = events;
		this.items = items;
		this.collections = collections;
		this.processor = processor;
		this.storage = storage;
	}

	/**
	 * Checks the photo count and processes every photo before anything is saved, so one bad file saves nothing.
	 * Empty files are ignored.
	 *
	 * @throws ApiException 400 {@code VALIDATION_ERROR} if there are no photos or more than 50; 400 {@code INVALID_IMAGE} for a bad file
	 */
	public List<ProcessedImage> process(List<MultipartFile> files) {
		List<MultipartFile> photos = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
		if (photos.isEmpty() || photos.size() > MAX_PHOTOS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					Map.of("files", "Choose between 1 and " + MAX_PHOTOS + " photos"));
		}
		return photos.stream().map(processor::process).toList();
	}

	/**
	 * Writes the files and the item rows. Call inside a transaction; every key written is added to storedKeys. Keys
	 * look like {@code <sellerId>/<uuid>.jpg} and {@code <sellerId>/<uuid>_thumb.jpg}. Every photo starts with the same
	 * quantity.
	 *
	 * @param storedKeys filled so the caller can delete the files if the transaction rolls back
	 */
	public void store(Long collectionId, Long sellerId, List<ProcessedImage> images, int quantity, List<String> storedKeys) {
		List<Item> rows = new ArrayList<>();
		for (ProcessedImage image : images) {
			String base = sellerId + "/" + UUID.randomUUID();
			String full = base + ".jpg";
			String thumb = base + "_thumb.jpg";
			storage.save(full, image.full());
			storedKeys.add(full);
			storage.save(thumb, image.thumb());
			storedKeys.add(thumb);
			Item row = new Item(collectionId, sellerId, full, thumb, image.hash());
			row.setQuantity(quantity);
			rows.add(row);
		}
		items.saveAll(rows);
	}

	/** The collection's photos as DTOs, in upload order, with the effective price worked out from the collection price. */
	public List<ItemDto> dtosFor(Long collectionId, int collectionPrice) {
		return items.findByCollectionIdOrderById(collectionId).stream().map(i -> ItemDto.of(i, collectionPrice)).toList();
	}

	/** Storage keys of every full image and thumbnail in the collection, read before the rows are deleted. */
	public List<String> fileKeysOf(Long collectionId) {
		return items.findByCollectionIdOrderById(collectionId)
			.stream()
			.flatMap(i -> java.util.stream.Stream.of(i.getImagePath(), i.getThumbPath()))
			.toList();
	}

	/** Best effort: a file that cannot be removed is logged, it must not fail the request. */
	public void deleteFiles(List<String> keys) {
		for (String key : keys) {
			try {
				storage.delete(key);
			}
			catch (RuntimeException e) {
				log.warn("Could not delete stored file {}", key, e);
			}
		}
	}

	/**
	 * Applies only the fields present in the body; an explicit null clears the field. colour is trimmed and at most 50
	 * characters (blank clears it), priceOverride is a whole number above 0, quantity is 0 to 10000, status is
	 * AVAILABLE or SOLD_OUT. Changing SOLD_OUT to AVAILABLE publishes {@link ItemsRestocked}. All errors are collected
	 * and reported together, and nothing is saved if there is one.
	 *
	 * @return the updated photo with its effective price
	 * @throws ApiException 400 {@code VALIDATION_ERROR} with the bad fields, 403 {@code FORBIDDEN} if another seller owns it, 404 {@code NOT_FOUND}
	 */
	@Transactional
	public ItemDto update(long sellerId, long itemId, JsonNode patch) {
		Item item = owned(itemId, sellerId);
		Map<String, String> errors = new LinkedHashMap<>();
		if (patch.has("colour")) {
			JsonNode node = patch.get("colour");
			if (node.isNull()) {
				item.setColour(null);
			}
			else if (node.isString() && node.asString().strip().length() <= MAX_COLOUR) {
				String colour = node.asString().strip();
				item.setColour(colour.isEmpty() ? null : colour);
			}
			else {
				errors.put("colour", "must be text of at most " + MAX_COLOUR + " characters");
			}
		}
		if (patch.has("priceOverride")) {
			JsonNode node = patch.get("priceOverride");
			if (node.isNull()) {
				item.setPriceOverride(null);
			}
			else if (node.isIntegralNumber() && node.canConvertToInt() && node.intValue() > 0) {
				item.setPriceOverride(node.intValue());
			}
			else {
				errors.put("priceOverride", "must be a whole number above 0");
			}
		}
		if (patch.has("quantity")) {
			JsonNode node = patch.get("quantity");
			if (node.isIntegralNumber() && node.canConvertToInt() && node.intValue() >= 0 && node.intValue() <= MAX_QUANTITY) {
				item.setQuantity(node.intValue());
			}
			else {
				errors.put("quantity", "must be a whole number from 0 to " + MAX_QUANTITY);
			}
		}
		if (patch.has("status")) {
			JsonNode node = patch.get("status");
			ItemStatus status = node.isString() ? parseStatus(node.asString()) : null;
			if (status == null) {
				errors.put("status", "must be AVAILABLE or SOLD_OUT");
			}
			else {
				// Going from sold out back to available is news for followers; the other way is not.
				if (item.getStatus() == ItemStatus.SOLD_OUT && status == ItemStatus.AVAILABLE) {
					events.publishEvent(new ItemsRestocked(sellerId, item.getCollectionId(), 1));
				}
				item.setStatus(status);
			}
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid", errors);
		}
		int price = collections.findById(item.getCollectionId()).map(SareeCollection::getPrice).orElse(0);
		return ItemDto.of(items.save(item), price);
	}

	/**
	 * Marks every photo of a collection available or sold out. The caller has already checked ownership. Only
	 * photos that really change count, so "mark all available" tells followers about exactly the ones restocked.
	 */
	@Transactional
	public void setStatusForCollection(long sellerId, Long collectionId, ItemStatus status) {
		int changed = items.setStatusForCollection(collectionId, status);
		if (status == ItemStatus.AVAILABLE && changed > 0) {
			events.publishEvent(new ItemsRestocked(sellerId, collectionId, changed));
		}
	}

	/** The status with this name, or null if there is none. */
	private static ItemStatus parseStatus(String value) {
		try {
			return ItemStatus.valueOf(value);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Deletes the row, then the stored files on a best-effort basis.
	 *
	 * @throws ApiException 403 {@code FORBIDDEN} if another seller owns it, 404 {@code NOT_FOUND}
	 */
	public void delete(long sellerId, long itemId) {
		Item item = owned(itemId, sellerId);
		items.delete(item);
		deleteFiles(List.of(item.getImagePath(), item.getThumbPath()));
	}

	/**
	 * Finds the stored file for a photo. Anyone logged in may read it; the photo is not tied to its seller here.
	 *
	 * @param thumb true for the thumbnail, false for the full image
	 * @throws ApiException 404 {@code NOT_FOUND} if the item or the file does not exist
	 */
	public Resource image(long itemId, boolean thumb) {
		Item item = items.findById(itemId).orElseThrow(ItemService::notFound);
		Resource resource = storage.load(thumb ? item.getThumbPath() : item.getImagePath());
		if (!resource.exists()) {
			throw notFound();
		}
		return resource;
	}

	/** Loads the item and checks the seller owns it (404 if missing, 403 if it is someone else's). */
	private Item owned(long itemId, long sellerId) {
		Item item = items.findById(itemId).orElseThrow(ItemService::notFound);
		if (!item.getSellerId().equals(sellerId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This photo belongs to another seller");
		}
		return item;
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Photo not found");
	}

}
