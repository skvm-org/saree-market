package com.sareemarket.seller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sareemarket.auth.UserRepository;
import com.sareemarket.collection.CollectionService;
import com.sareemarket.common.ApiException;
import com.sareemarket.follow.FollowService;
import com.sareemarket.follow.NotifyMode;
import com.sareemarket.seller.dto.SellerDetailDto;
import com.sareemarket.seller.dto.SellerListDto;
import com.sareemarket.seller.dto.SellerProfileDto;
import com.sareemarket.seller.dto.SellerProfileRequest;

@Service
public class SellerService {

	private final SellerProfileRepository profiles;

	private final UserRepository users;

	private final CollectionService collections;

	private final FollowService follows;

	SellerService(SellerProfileRepository profiles, UserRepository users, CollectionService collections,
			FollowService follows) {
		this.follows = follows;
		this.profiles = profiles;
		this.users = users;
		this.collections = collections;
	}

	public List<SellerListDto> list() {
		return profiles.listEnabled();
	}

	public SellerDetailDto get(long id, long viewerId, boolean viewerIsReseller) {
		SellerProfile profile = profiles.findById(id)
			.filter(p -> users.findById(p.getUserId()).map(u -> u.isEnabled()).orElse(false))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found"));
		return new SellerDetailDto(profile.getUserId(), profile.getShopName(), profile.getMarketLocation(),
				profile.getContactPhone(), profile.getWhatsappNumber(),
				followMode(viewerId, id, viewerIsReseller), collections.listMine(id));
	}

	public SellerProfileDto mine(long sellerId) {
		return toDto(find(sellerId));
	}

	@Transactional
	public SellerProfileDto updateMine(long sellerId, SellerProfileRequest r) {
		SellerProfile profile = find(sellerId);
		String whatsapp = r.whatsappNumber() == null || r.whatsappNumber().isBlank() ? null : r.whatsappNumber();
		profile.update(r.shopName().strip(), r.marketLocation().strip(), r.contactPhone(), whatsapp);
		return toDto(profile);
	}

	private SellerProfile find(long sellerId) {
		return profiles.findById(sellerId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found"));
	}

	private static SellerProfileDto toDto(SellerProfile p) {
		return new SellerProfileDto(p.getShopName(), p.getMarketLocation(), p.getContactPhone(), p.getWhatsappNumber());
	}

	private String followMode(long viewerId, long sellerId, boolean viewerIsReseller) {
		NotifyMode mode = viewerIsReseller ? follows.modeOf(viewerId, sellerId) : null;
		return mode == null ? null : mode.name();
	}

}
