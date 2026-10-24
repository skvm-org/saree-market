package com.sareemarket.item;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findByCollectionIdOrderById(Long collectionId);

}
