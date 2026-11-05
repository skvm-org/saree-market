package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The profile-picture version rides along wherever the server returns a seller or customer; an older server sends none. */
class ProfilePhotoModelsTest {

    @Test
    fun theUserCarriesItsPictureVersion() {
        val user = testJson.decodeFromString<UserDto>("""{"id":7,"name":"Priya","phone":"9876543210","role":"CUSTOMER","photoVersion":1793908933926}""")

        assertEquals(1793908933926L, user.photoVersion)
    }

    @Test
    fun aUserFromAnOlderServerHasNoVersion() {
        val user = testJson.decodeFromString<UserDto>("""{"id":7,"name":"Priya","phone":"9876543210","role":"CUSTOMER"}""")

        assertNull(user.photoVersion)
    }

    @Test
    fun aCatalogCardCarriesTheSellersVersion() {
        val card = testJson.decodeFromString<CollectionSummary>(
            """{"id":1,"title":"Diwali","price":2500,"itemCount":3,"availableCount":2,"soldOutCount":1,"sellerId":2,"shopName":"Lakshmi Silks","sellerPhotoVersion":55}""",
        )

        assertEquals(55L, card.sellerPhotoVersion)
        assertEquals(2L, card.sellerId)
    }

    @Test
    fun thePhotoPageTheShopPageAndFollowsCarryIt() {
        val detail = testJson.decodeFromString<SellerContact>("""{"id":2,"shopName":"S","marketLocation":"M","photoVersion":11}""")
        val page = testJson.decodeFromString<SellerPage>("""{"id":2,"shopName":"S","marketLocation":"M","photoVersion":12}""")
        val follow = testJson.decodeFromString<FollowItem>("""{"sellerId":2,"shopName":"S","marketLocation":"M","notifyMode":"INSTANT","sellerPhotoVersion":13}""")

        assertEquals(11L, detail.photoVersion)
        assertEquals(12L, page.photoVersion)
        assertEquals(13L, follow.sellerPhotoVersion)
    }

    @Test
    fun aNotificationCarriesTheSellersVersion() {
        val note = testJson.decodeFromString<AppNotification>(
            """{"id":1,"type":"ORDER_UPDATE","itemCount":1,"sellerId":2,"shopName":"S","message":"m","read":false,"createdAt":"2026-11-06T10:00:00Z","updatedAt":"2026-11-06T10:00:00Z","orderId":5,"orderStatus":"SHIPPED","sellerPhotoVersion":14}""",
        )

        assertEquals(14L, note.sellerPhotoVersion)
    }

    @Test
    fun theCustomerOnASellersOrderCarriesTheIdAndVersion() {
        val party = testJson.decodeFromString<OrderParty>("""{"id":9,"name":"Priya","phone":"9876543210","photoVersion":99}""")
        val old = testJson.decodeFromString<OrderParty>("""{"name":"Priya","phone":"9876543210"}""")

        assertEquals(9L, party.id)
        assertEquals(99L, party.photoVersion)
        assertNull(old.id)
        assertNull(old.photoVersion)
    }

    @Test
    fun noneOfTheOlderShapesFail() {
        val card = testJson.decodeFromString<CollectionSummary>("""{"id":1,"title":"Diwali","price":2500,"itemCount":3,"availableCount":2,"soldOutCount":1}""")

        assertNull(card.sellerPhotoVersion)
    }
}
