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

/**
 * Shop profiles: the public shop list and detail for resellers, and a seller editing their own profile.
 * Used by SellerController and SellerProfileController. A seller whose account is disabled is hidden from resellers.
 */
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

	/**
	 * Enabled sellers by shop name, without contact details.
	 */
	public List<SellerListDto> list() {
		return profiles.listEnabled();
	}

	/**
	 * Shop detail for a reseller or admin: contact numbers, the seller's collections and the viewer's follow state.
	 *
	 * @param viewerId the signed-in user; only used to look up the follow state
	 * @param viewerIsReseller false for an admin, who never follows, so the follow state is null
	 * @throws ApiException 404 NOT_FOUND when the seller does not exist or the account is disabled
	 */
	public SellerDetailDto get(long id, long viewerId, boolean viewerIsReseller) {
		SellerProfile profile = profiles.findById(id)
			.filter(p -> users.findById(p.getUserId()).map(u -> u.isEnabled()).orElse(false))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found"));
		return new SellerDetailDto(profile.getUserId(), profile.getShopName(), profile.getMarketLocation(),
				profile.getContactPhone(), profile.getWhatsappNumber(),
				followMode(viewerId, id, viewerIsReseller), collections.listMine(id));
	}

	/**
	 * The seller's own profile, including the UPI ID and shipping fee.
	 *
	 * @throws ApiException 404 NOT_FOUND when the seller has no profile
	 */
	public SellerProfileDto mine(long sellerId) {
		return toDto(find(sellerId));
	}

	/**
	 * Saves the seller's shop details and payment settings. Names are trimmed and a blank WhatsApp number is stored
	 * as null. upiId and shippingFee are optional so older apps that do not send them leave the saved values alone.
	 *
	 * @throws ApiException 404 NOT_FOUND when the seller has no profile
	 */
	@Transactional
	public SellerProfileDto updateMine(long sellerId, SellerProfileRequest r) {
		SellerProfile profile = find(sellerId);
		String whatsapp = r.whatsappNumber() == null || r.whatsappNumber().isBlank() ? null : r.whatsappNumber();
		profile.update(r.shopName().strip(), r.marketLocation().strip(), r.contactPhone(), whatsapp);
		// Absent means unchanged, so an older app that does not know these fields cannot wipe them; "" clears the UPI ID.
		String upi = r.upiId() == null ? profile.getUpiId() : r.upiId().isBlank() ? null : r.upiId();
		profile.updatePayment(upi, r.shippingFee() == null ? profile.getShippingFee() : r.shippingFee());
		return toDto(profile);
	}

	private SellerProfile find(long sellerId) {
		return profiles.findById(sellerId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Seller not found"));
	}

	private static SellerProfileDto toDto(SellerProfile p) {
		return new SellerProfileDto(p.getShopName(), p.getMarketLocation(), p.getContactPhone(), p.getWhatsappNumber(),
				p.getUpiId(), p.getShippingFee());
	}

	/**
	 * Name of the viewer's follow mode for this seller, or null when not following or not a reseller.
	 */
	private String followMode(long viewerId, long sellerId, boolean viewerIsReseller) {
		NotifyMode mode = viewerIsReseller ? follows.modeOf(viewerId, sellerId) : null;
		return mode == null ? null : mode.name();
	}

}
