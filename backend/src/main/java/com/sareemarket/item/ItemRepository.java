package com.sareemarket.item;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ItemRepository extends JpaRepository<Item, Long> {

	List<Item> findByCollectionIdOrderById(Long collectionId);

	/** Sets every item of a collection in one statement. Bulk updates skip @UpdateTimestamp, so it is set here. */
	@Modifying
	@Transactional
	@Query("update Item i set i.status = :status, i.updatedAt = CURRENT_TIMESTAMP "
			+ "where i.collectionId = :collectionId and i.status <> :status")
	int setStatusForCollection(@Param("collectionId") Long collectionId, @Param("status") ItemStatus status);

}
