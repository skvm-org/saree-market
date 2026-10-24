package com.sareemarket.seller;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sareemarket.seller.dto.SellerListDto;

public interface SellerProfileRepository extends JpaRepository<SellerProfile, Long> {

	/** Sellers whose account is switched on, by shop name. */
	@Query("""
			select new com.sareemarket.seller.dto.SellerListDto(s.userId, s.shopName, s.marketLocation)
			from SellerProfile s join AppUser u on u.id = s.userId
			where u.enabled = true
			order by lower(s.shopName), s.userId""")
	List<SellerListDto> listEnabled();

}
