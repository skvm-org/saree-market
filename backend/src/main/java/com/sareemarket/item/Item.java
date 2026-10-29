package com.sareemarket.item;

import java.time.Instant;

import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** DynamicUpdate: a seller's edit writes only the fields that changed, so it cannot overwrite stock that checkout just moved. */
@Entity
@DynamicUpdate
@Table(name = "item")
public class Item {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long collectionId;

	private Long sellerId;

	private String colour;

	private Integer priceOverride;

	private int quantity = 1;

	@Enumerated(EnumType.STRING)
	private ItemStatus status = ItemStatus.AVAILABLE;

	private String imagePath;

	private String thumbPath;

	private long imageHash;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	protected Item() {
	}

	public Item(Long collectionId, Long sellerId, String imagePath, String thumbPath, long imageHash) {
		this.collectionId = collectionId;
		this.sellerId = sellerId;
		this.imagePath = imagePath;
		this.thumbPath = thumbPath;
		this.imageHash = imageHash;
	}

	public Long getId() {
		return id;
	}

	public Long getCollectionId() {
		return collectionId;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public String getColour() {
		return colour;
	}

	public void setColour(String colour) {
		this.colour = colour;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public Integer getPriceOverride() {
		return priceOverride;
	}

	public void setPriceOverride(Integer priceOverride) {
		this.priceOverride = priceOverride;
	}

	public ItemStatus getStatus() {
		return status;
	}

	public void setStatus(ItemStatus status) {
		this.status = status;
	}

	public String getImagePath() {
		return imagePath;
	}

	public String getThumbPath() {
		return thumbPath;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
