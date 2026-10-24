package com.sareemarket.collection;

import java.time.Instant;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "collection")
public class SareeCollection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long sellerId;

	private String title;

	private Long sareeTypeId;

	private Long fabricId;

	private int price;

	private String description;

	@Column(insertable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	protected SareeCollection() {
	}

	public SareeCollection(Long sellerId, String title, Long sareeTypeId, Long fabricId, int price,
			String description) {
		this.sellerId = sellerId;
		this.title = title;
		this.sareeTypeId = sareeTypeId;
		this.fabricId = fabricId;
		this.price = price;
		this.description = description;
	}

	public void update(String title, Long sareeTypeId, Long fabricId, int price, String description) {
		this.title = title;
		this.sareeTypeId = sareeTypeId;
		this.fabricId = fabricId;
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

	public Long getSareeTypeId() {
		return sareeTypeId;
	}

	public Long getFabricId() {
		return fabricId;
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
