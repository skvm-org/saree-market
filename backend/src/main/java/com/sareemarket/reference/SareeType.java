package com.sareemarket.reference;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "saree_type")
public class SareeType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private boolean active = true;

	protected SareeType() {
	}

	public SareeType(String name) {
		this.name = name;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public boolean isActive() {
		return active;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

}
