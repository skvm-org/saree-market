package com.sareemarket.collection;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A collection: a title, a price and a description that a seller puts on a group of sarees (the item rows
 * point back to it by collection_id). price is in whole rupees and is the default price of every photo in it,
 * unless the photo has its own. description may be null. createdAt is set by the database and never changes.
 */
@Entity
@Table(name = "collection")
public class SareeCollection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long sellerId;

	private String title;

	private int price;

	private String description;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	protected SareeCollection() {
	}

	public SareeCollection(Long sellerId, String title, int price, String description) {
		this.sellerId = sellerId;
		this.title = title;
		this.price = price;
		this.description = description;
	}

	/**
	 * Replaces the editable fields. The seller and the creation time do not change.
	 */
	public void update(String title, int price, String description) {
		this.title = title;
		this.price = price;
		this.description = description;
	}

	public Long getId() {
		return id;
	}

	public Long getSellerId() {
		return sellerId;
	}

	public String getTitle() {
		return title;
	}

	public int getPrice() {
		return price;
	}

	public String getDescription() {
		return description;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
