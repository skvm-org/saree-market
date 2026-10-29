package com.sareemarket.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderModelsTest {

    @Test
    fun decodesEveryStatusTheBackendCanSend() {
        listOf("PAYMENT_SUBMITTED", "PAYMENT_REJECTED", "CONFIRMED", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED").forEach {
            assertEquals(it, testJson.decodeFromString<OrderStatus>("\"$it\"").name)
        }
    }

    @Test
    fun activeStatusesAreTheOnesStillInProgress() {
        assertTrue(OrderStatus.PAYMENT_REJECTED.isActive)
        assertTrue(OrderStatus.SHIPPED.isActive)
        assertFalse(OrderStatus.DELIVERED.isActive)
        assertFalse(OrderStatus.CANCELLED.isActive)
    }

    @Test
    fun decodesAnOrderAsTheBackendSendsIt() {
        val order = testJson.decodeFromString<Order>(
            """{"id":1,"status":"SHIPPED","sellerId":2,"shopName":"Lakshmi Silks","sellerUpiId":"lakshmi@okaxis",
            "customer":null,"lines":[{"itemId":3,"title":"Diwali soft silks","colour":"Bottle green","unitPrice":4500,"quantity":1}],
            "itemsTotal":4500,"shippingFee":60,"grandTotal":4560,
            "address":{"name":"Home","phone":"9876543210","line1":"12 Gandhi Rd","line2":null,"city":"Erode","state":"Tamil Nadu","pin":"638001"},
            "note":null,"paymentReference":"412345678901","hasProof":true,"rejectionReason":null,"paymentDeadline":null,
            "cancelReason":null,"cancelledBy":null,"courier":"DTDC","trackingNumber":"D1","unread":true,
            "createdAt":"2026-10-28T16:00:00Z","updatedAt":"2026-10-28T17:00:00Z",
            "history":[{"status":"PAYMENT_SUBMITTED","at":"2026-10-28T16:00:00Z"},{"status":"SHIPPED","at":"2026-10-28T17:00:00Z"}],
            "somethingNew":1}""",
        )

        assertEquals(OrderStatus.SHIPPED, order.status)
        assertEquals(4560, order.grandTotal)
        assertEquals("D1", order.trackingNumber)
        assertEquals(2, order.history.size)
    }

    @Test
    fun decodesTheCustomerPartyInTheSellersView() {
        val order = testJson.decodeFromString<Order>(
            """{"id":1,"status":"PAYMENT_SUBMITTED","sellerId":2,"shopName":"S","sellerUpiId":"s@ybl",
            "customer":{"name":"Priya","phone":"9876543210"},"lines":[],"itemsTotal":0,"shippingFee":0,"grandTotal":0,
            "address":{"name":"H","phone":"9","line1":"l","line2":null,"city":"c","state":"s","pin":"638001"},
            "note":null,"paymentReference":"412345678901","hasProof":true,"createdAt":"2026-10-28T16:00:00Z","updatedAt":"2026-10-28T16:00:00Z"}""",
        )

        assertEquals("Priya", order.customer?.name)
        assertEquals("9876543210", order.customer?.phone)
    }

    @Test
    fun decodesTheCustomerPartyInTheSellersViewAbsentForCustomers() {
        val order = testJson.decodeFromString<Order>(
            """{"id":1,"status":"CONFIRMED","sellerId":2,"shopName":"S","lines":[],"itemsTotal":0,"shippingFee":0,"grandTotal":0,
            "address":{"name":"H","phone":"9","line1":"l","city":"c","state":"s","pin":"638001"},
            "createdAt":"2026-10-28T16:00:00Z","updatedAt":"2026-10-28T16:00:00Z"}""",
        )

        assertEquals(null, order.customer)
    }

    @Test
    fun decodesTheHoldAndCheckoutResult() {
        val result = testJson.decodeFromString<CheckoutResult>(
            """{"holds":[{"id":1,"sellerId":2,"shopName":"S","sellerUpiId":"s@ybl","lines":[],"itemsTotal":0,"shippingFee":0,
            "grandTotal":0,"address":{"name":"H","phone":"9","line1":"l","line2":null,"city":"c","state":"s","pin":"638001"},
            "note":null,"expiresAt":"2026-10-28T21:53:54.581761Z"}],
            "adjustments":[{"itemId":3,"title":"t","requested":4,"reserved":2}],
            "skipped":[{"sellerId":4,"shopName":"X","reason":"NOT_ACCEPTING_ORDERS"}]}""",
        )

        assertEquals(1, result.holds.size)
        assertEquals("NOT_ACCEPTING_ORDERS", result.skipped.single().reason)
    }

    @Test
    fun decodesTheCartAsTheBackendSendsIt() {
        val cart = testJson.decodeFromString<Cart>(
            """{"sellers":[{"sellerId":6,"shopName":"Meenakshi Silks","acceptsOrders":false,"shippingFee":0,
            "lines":[{"itemId":25,"collectionTitle":"Phone test silks","colour":null,"unitPrice":3200,"quantity":1,"maxQuantity":1,"inStock":true,"lineTotal":3200}],
            "itemsTotal":3200,"grandTotal":3200}],"lineCount":1}""",
        )

        assertEquals(3200, cart.sellers.single().grandTotal)
        assertFalse(cart.sellers.single().acceptsOrders)
    }

    @Test
    fun anOldItemJsonWithoutStockFieldsStillDecodes() {
        val item = testJson.decodeFromString<CatalogItem>(
            """{"id":1,"collectionId":2,"collectionTitle":"t","sareeType":"s","fabric":"f","price":100,"status":"AVAILABLE","sellerId":3,"shopName":"x"}""",
        )

        assertEquals(1, item.quantity)
        assertTrue(item.inStock)
    }
}
