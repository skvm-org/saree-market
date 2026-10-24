package com.sareemarket.collection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.sareemarket.item.ProcessedImage;
import com.sareemarket.reference.FabricRepository;
import com.sareemarket.reference.RefDto;
import com.sareemarket.reference.SareeTypeRepository;

@Service
public class CollectionService {

	private final SareeCollectionRepository collections;

	private final SareeTypeRepository types;

	private final FabricRepository fabrics;

	private final ItemService items;

	private final TransactionTemplate tx;

	CollectionService(SareeCollectionRepository collections, SareeTypeRepository types, FabricRepository fabrics,
			ItemService items, TransactionTemplate tx) {
		this.collections = collections;
		this.types = types;
		this.fabrics = fabrics;
		this.items = items;
		this.tx = tx;
	}

	public CollectionDetailDto create(long sellerId, CreateCollectionForm form) {
		checkReferences(form.sareeTypeId(), form.fabricId());
		List<ProcessedImage> images = items.process(form.files());
		Long id = saveWithPhotos(sellerId, images, () -> collections.save(new SareeCollection(sellerId,
				form.title().strip(), form.sareeTypeId(), form.fabricId(), form.price(), blankToNull(form.description()))));
		return detail(id);
	}

	public CollectionDetailDto addPhotos(long sellerId, long collectionId, List<MultipartFile> files) {
		SareeCollection collection = owned(collectionId, sellerId);
		List<ProcessedImage> images = items.process(files);
		saveWithPhotos(sellerId, images, () -> collection);
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
		return collections.summariesFor(sellerId);
	}

	public CollectionDetailDto update(long sellerId, long id, UpdateCollectionRequest request) {
		SareeCollection collection = owned(id, sellerId);
		checkReferences(request.sareeTypeId(), request.fabricId());
		collection.update(request.title().strip(), request.sareeTypeId(), request.fabricId(), request.price(),
				blankToNull(request.description()));
		return detail(collections.save(collection));
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
	private Long saveWithPhotos(long sellerId, List<ProcessedImage> images,
			java.util.function.Supplier<SareeCollection> collection) {
		List<String> storedKeys = new ArrayList<>();
		try {
			return tx.execute(status -> {
				Long id = collection.get().getId();
				items.store(id, sellerId, images, storedKeys);
				return id;
			});
		}
		catch (RuntimeException e) {
			items.deleteFiles(storedKeys);
			throw e;
		}
	}

	private void checkReferences(Long typeId, Long fabricId) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (types.findById(typeId).filter(t -> t.isActive()).isEmpty()) {
			errors.put("sareeTypeId", "Unknown saree type");
		}
		if (fabrics.findById(fabricId).filter(f -> f.isActive()).isEmpty()) {
			errors.put("fabricId", "Unknown fabric");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Some fields are invalid", errors);
		}
	}

	private CollectionDetailDto detail(Long id) {
		return detail(find(id));
	}

	private CollectionDetailDto detail(SareeCollection c) {
		RefDto type = types.findById(c.getSareeTypeId()).map(t -> new RefDto(t.getId(), t.getName())).orElseThrow();
		RefDto fabric = fabrics.findById(c.getFabricId()).map(f -> new RefDto(f.getId(), f.getName())).orElseThrow();
		return new CollectionDetailDto(c.getId(), c.getSellerId(), c.getTitle(), type, fabric, c.getPrice(),
				c.getDescription(), c.getCreatedAt(), c.getUpdatedAt(), items.dtosFor(c.getId(), c.getPrice()));
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
