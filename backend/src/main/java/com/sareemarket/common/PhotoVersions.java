package com.sareemarket.common;

/**
 * The profile-picture version (see UserDto.photoVersion) of another account, for queries that return a seller or customer
 * next to their id. Clients build {@code /api/v1/users/{id}/photo?v=version}, so a changed picture is fetched again and an
 * unchanged one stays cached.
 */
public final class PhotoVersions {

	private PhotoVersions() {
	}

	/**
	 * A SQL scalar expression: when the account whose id is in {@code idColumn} (for example {@code cc.seller_id}) last
	 * changed its picture, in whole epoch milliseconds (the pictures are stamped at millisecond precision, so this equals
	 * {@code AppUser.photoVersion()} exactly), or NULL when it has none. {@code idColumn} is code, never user input.
	 */
	public static String sql(String idColumn) {
		return "(SELECT floor(extract(epoch FROM pv.photo_updated_at) * 1000)::bigint FROM app_user pv WHERE pv.id = " + idColumn
				+ " AND pv.photo_key IS NOT NULL)";
	}

}
