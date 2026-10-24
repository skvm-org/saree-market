package com.sareemarket.collection;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sareemarket.collection.dto.CollectionSummaryDto;

public interface SareeCollectionRepository extends JpaRepository<SareeCollection, Long> {

	@Query("""
			select new com.sareemarket.collection.dto.CollectionSummaryDto(
			    c.id, c.title, t.name, f.name, c.price, c.createdAt,
			    count(i), count(case when i.status = com.sareemarket.item.ItemStatus.AVAILABLE then 1 end), min(i.id))
			from SareeCollection c
			join SareeType t on t.id = c.sareeTypeId
			join Fabric f on f.id = c.fabricId
			left join Item i on i.collectionId = c.id
			where c.sellerId = :sellerId
			group by c.id, c.title, t.name, f.name, c.price, c.createdAt
			order by c.createdAt desc, c.id desc""")
	List<CollectionSummaryDto> summariesFor(@Param("sellerId") Long sellerId);

}
