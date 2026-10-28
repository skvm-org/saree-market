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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sareemarket.collection.SareeCollection;
import com.sareemarket.collection.SareeCollectionRepository;
import com.sareemarket.common.ApiException;
import com.sareemarket.common.ImageStorage;
import com.sareemarket.item.dto.ItemDto;
import com.sareemarket.item.event.CollectionSoldOut;
import com.sareemarket.item.event.ItemSoldOut;
import com.sareemarket.notification.event.ItemsRestocked;

import tools.jackson.databind.JsonNode;

@Service
public class ItemService {

	static final int MAX_PHOTOS = 30;

	private static final int MAX_COLOUR = 50;

	private static final Logger log = LoggerFactory.getLogger(ItemService.class);

	private final ItemRepository items;

	private final SareeCollectionRepository collections;

	private final ImageProcessor processor;

	private final ImageStorage storage;

	private final ApplicationEventPublisher events;

	private final NamedParameterJdbcTemplate jdbc;

	ItemService(ItemRepository items, SareeCollectionRepository collections, ImageProcessor processor,
			ImageStorage storage, ApplicationEventPublisher events, NamedParameterJdbcTemplate jdbc) {
		this.events = events;
		this.jdbc = jdbc;
		this.items = items;
		this.collections = collections;
		this.processor = processor;
		this.storage = storage;
	}

	/** Checks the photo count and processes every photo before anything is saved, so one bad file saves nothing. */
	public List<ProcessedImage> process(List<MultipartFile> files) {
		List<MultipartFile> photos = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
		if (photos.isEmpty() || photos.size() > MAX_PHOTOS) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid",
					Map.of("files", "Choose between 1 and " + MAX_PHOTOS + " photos"));
		}
		return photos.stream().map(processor::process).toList();
	}

	/** Writes the files and the item rows. Call inside a transaction; every key written is added to storedKeys. */
	public void store(Long collectionId, Long sellerId, List<ProcessedImage> images, List<String> storedKeys) {
		List<Item> rows = new ArrayList<>();
		for (ProcessedImage image : images) {
			String base = sellerId + "/" + UUID.randomUUID();
			String full = base + ".jpg";
			String thumb = base + "_thumb.jpg";
			storage.save(full, image.full());
			storedKeys.add(full);
			storage.save(thumb, image.thumb());
			storedKeys.add(thumb);
			rows.add(new Item(collectionId, sellerId, full, thumb, image.hash()));
		}
		items.saveAll(rows);
	}

	public List<ItemDto> dtosFor(Long collectionId, int collectionPrice) {
		return items.findByCollectionIdOrderById(collectionId).stream().map(i -> ItemDto.of(i, collectionPrice)).toList();
	}

	/** Open (pending or accepted) booking requests per photo; only photos that have some are in the map. */
	public Map<Long, Integer> openRequestCounts(java.util.Collection<Long> itemIds) {
		Map<Long, Integer> counts = new java.util.HashMap<>();
		if (itemIds.isEmpty()) {
			return counts;
		}
		jdbc.query("SELECT item_id, count(*) AS n FROM booking_request WHERE item_id IN (:ids) "
				+ "AND status IN ('PENDING', 'ACCEPTED') GROUP BY item_id", new MapSqlParameterSource("ids", itemIds),
				rs -> {
					counts.put(rs.getLong("item_id"), rs.getInt("n"));
				});
		return counts;
	}

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

	/** Applies only the fields present in the body; an explicit null clears the field. */
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
		if (patch.has("status")) {
			JsonNode node = patch.get("status");
			ItemStatus status = node.isString() ? parseStatus(node.asString()) : null;
			if (status == null || status == ItemStatus.ON_HOLD) {
				errors.put("status", "must be AVAILABLE or SOLD_OUT");
			}
			else {
				if (item.getStatus() == ItemStatus.ON_HOLD && status == ItemStatus.AVAILABLE) {
					throw new ApiException(HttpStatus.CONFLICT, "ON_HOLD",
							"This saree is on hold; reject or release its booking request instead");
				}
				// Going from sold out back to available is news for followers; the other way is not.
				if (item.getStatus() == ItemStatus.SOLD_OUT && status == ItemStatus.AVAILABLE) {
					events.publishEvent(new ItemsRestocked(sellerId, item.getCollectionId(), 1));
				}
				// Selling a held saree sells it to the holder; other open requests on it are closed.
				if (item.getStatus() != ItemStatus.SOLD_OUT && status == ItemStatus.SOLD_OUT) {
					events.publishEvent(new ItemSoldOut(itemId));
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
		if (status == ItemStatus.SOLD_OUT && changed > 0) {
			events.publishEvent(new CollectionSoldOut(collectionId));
		}
	}

	private static ItemStatus parseStatus(String value) {
		try {
			return ItemStatus.valueOf(value);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

	public void delete(long sellerId, long itemId) {
		Item item = owned(itemId, sellerId);
		items.delete(item);
		deleteFiles(List.of(item.getImagePath(), item.getThumbPath()));
	}

	public Resource image(long itemId, boolean thumb) {
		Item item = items.findById(itemId).orElseThrow(ItemService::notFound);
		Resource resource = storage.load(thumb ? item.getThumbPath() : item.getImagePath());
		if (!resource.exists()) {
			throw notFound();
		}
		return resource;
	}

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
