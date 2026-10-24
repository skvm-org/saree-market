package com.sareemarket.seller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sareemarket.auth.UserRepository;
import com.sareemarket.collection.CollectionService;
import com.sareemarket.common.ApiException;
import com.sareemarket.seller.dto.SellerDetailDto;
import com.sareemarket.seller.dto.SellerListDto;

@Service
public class SellerService {

	private final SellerProfileRepository profiles;

	private final UserRepository users;

	private final CollectionService collections;

	SellerService(SellerProfileRepository profiles, UserRepository users, CollectionService collections) {
		this.profiles = profiles;
		this.users = users;
		this.collections = collections;
	}

	public List<SellerListDto> list() {
		return profiles.listEnabled();
	}

	public SellerDetailDto get(long id) {
		SellerProfile profile = profiles.findById(id)
			.filter(p -> users.findById(p.getUserId()).map(u -> u.isEnabled()).orElse(false))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found"));
		return new SellerDetailDto(profile.getUserId(), profile.getShopName(), profile.getMarketLocation(),
				profile.getContactPhone(), profile.getWhatsappNumber(), collections.listMine(id));
	}

}
