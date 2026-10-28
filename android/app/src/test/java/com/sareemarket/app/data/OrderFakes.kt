package com.sareemarket.app.data

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response

fun cartLine(itemId: Long, quantity: Int = 1, max: Int = 5, inStock: Boolean = true, price: Int = 800) = CartLine(
    itemId = itemId, collectionTitle = "Diwali silks", colour = "maroon", unitPrice = price, quantity = quantity,
    maxQuantity = max, inStock = inStock, lineTotal = if (inStock) price * minOf(quantity, max) else 0,
)

fun cartSeller(
    sellerId: Long = 1,
    lines: List<CartLine> = listOf(cartLine(10)),
    acceptsOrders: Boolean = true,
    fee: Int = 50,
) = CartSeller(
    sellerId, "Lakshmi Silks", acceptsOrders, fee, lines,
    itemsTotal = lines.sumOf { it.lineTotal },
    grandTotal = if (lines.any { it.inStock }) lines.sumOf { it.lineTotal } + fee else 0,
)

fun cartOf(vararg sellers: CartSeller) = Cart(sellers.toList(), sellers.sumOf { it.lines.size })

val homeAddress = Address(1, "Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001", true)

val snapshot = AddressSnapshot("Home", "9876543210", "12 Gandhi Road", null, "Erode", "Tamil Nadu", "638001")

fun hold(id: Long, expiresAt: String = "2099-01-01T12:00:00Z", total: Int = 850) = Hold(
    id, 1, "Lakshmi Silks", "lakshmi@okaxis", listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), 800, 50, total,
    snapshot, null, expiresAt,
)

fun order(
    id: Long,
    status: OrderStatus = OrderStatus.PAYMENT_SUBMITTED,
    unread: Boolean = false,
    rejectionReason: String? = null,
    paymentDeadline: String? = null,
    history: List<StatusEntry> = emptyList(),
) = Order(
    id = id, status = status, sellerId = 1, shopName = "Lakshmi Silks", sellerUpiId = "lakshmi@okaxis",
    lines = listOf(OrderLine(10, "Diwali silks", "maroon", 800, 1)), itemsTotal = 800, shippingFee = 50, grandTotal = 850,
    address = snapshot, note = null, paymentReference = "412345678901", hasProof = false, rejectionReason = rejectionReason,
    paymentDeadline = paymentDeadline, cancelReason = null, cancelledBy = null, courier = null, trackingNumber = null,
    unread = unread, createdAt = "2026-10-28T10:00:00Z", updatedAt = "2026-10-28T10:00:00Z", history = history,
)

fun orderPage(page: Int, totalPages: Int, perPage: Int = 2, status: OrderStatus = OrderStatus.PAYMENT_SUBMITTED) = OrderPage(
    items = (0 until perPage).map { order(page * 100L + it, status) },
    page = page, pageSize = perPage, totalItems = totalPages.toLong() * perPage, totalPages = totalPages,
)

/** Answers every call with something sensible; tests replace the lambdas they care about and read [calls]. */
class FakeShoppingApi : ShoppingApi {
    val calls = mutableListOf<String>()
    var cart: () -> Cart = { Cart() }
    var onSetLine: (Long, Int) -> Response<Unit> = { _, _ -> Response.success(Unit) }
    var onRemoveLine: (Long) -> Response<Unit> = { Response.success(Unit) }
    var onWishlist: (Int) -> CatalogPage = { page -> CatalogPage(emptyList(), page, 20, 0, 0) }
    var onAddress: (AddressForm) -> Address = { homeAddress }

    override suspend fun cart(): Cart {
        calls += "cart"
        return cart.invoke()
    }

    override suspend fun setLine(itemId: Long, body: QuantityBody): Response<Unit> {
        calls += "setLine $itemId ${body.quantity}"
        return onSetLine(itemId, body.quantity)
    }

    override suspend fun removeLine(itemId: Long): Response<Unit> {
        calls += "removeLine $itemId"
        return onRemoveLine(itemId)
    }

    override suspend fun wishlist(page: Int): CatalogPage {
        calls += "wishlist $page"
        return onWishlist(page)
    }

    override suspend fun addToWishlist(itemId: Long): Response<Unit> {
        calls += "wish+ $itemId"
        return Response.success(Unit)
    }

    override suspend fun removeFromWishlist(itemId: Long): Response<Unit> {
        calls += "wish- $itemId"
        return Response.success(Unit)
    }

    override suspend fun addresses(): List<Address> {
        calls += "addresses"
        return listOf(homeAddress)
    }

    override suspend fun createAddress(form: AddressForm): Address {
        calls += "createAddress ${form.name}"
        return onAddress(form)
    }

    override suspend fun updateAddress(id: Long, form: AddressForm): Address {
        calls += "updateAddress $id ${form.name}"
        return onAddress(form)
    }

    override suspend fun deleteAddress(id: Long): Response<Unit> {
        calls += "deleteAddress $id"
        return Response.success(Unit)
    }
}

class FakeOrderApi : OrderApi {
    val calls = mutableListOf<String>()
    var onCheckout: (CheckoutBody) -> CheckoutResult = { CheckoutResult(listOf(hold(1)), emptyList(), emptyList()) }
    var onPay: (Long, String, Boolean) -> Order = { _, _, _ -> com.sareemarket.app.data.order(5) }
    var onMine: (String?, Int) -> OrderPage = { _, page -> orderPage(page, totalPages = 1) }
    var onOrder: (Long) -> Order = { com.sareemarket.app.data.order(it) }
    var onCancel: (Long) -> Order = { com.sareemarket.app.data.order(it, OrderStatus.CANCELLED) }
    var summary: () -> OrderSummary = { OrderSummary() }
    var holds: () -> List<Hold> = { listOf(hold(1)) }

    override suspend fun checkout(body: CheckoutBody): CheckoutResult {
        calls += "checkout ${body.addressId} ${body.note} ${body.sellerId}"
        return onCheckout(body)
    }

    override suspend fun holds(): List<Hold> {
        calls += "holds"
        return holds.invoke()
    }

    override suspend fun cancelHold(id: Long): Response<Unit> {
        calls += "cancelHold $id"
        return Response.success(Unit)
    }

    override suspend fun payHold(id: Long, utr: RequestBody, proof: MultipartBody.Part?): Order {
        calls += "payHold $id ${proof != null}"
        return onPay(id, utr.text(), proof != null)
    }

    override suspend fun resubmit(id: Long, utr: RequestBody, proof: MultipartBody.Part?): Order {
        calls += "resubmit $id ${proof != null}"
        return onPay(id, utr.text(), proof != null)
    }

    override suspend fun mine(state: String?, page: Int): OrderPage {
        calls += "mine $state $page"
        return onMine(state, page)
    }

    override suspend fun order(id: Long): Order {
        calls += "order $id"
        return onOrder(id)
    }

    override suspend fun cancel(id: Long): Order {
        calls += "cancel $id"
        return onCancel(id)
    }

    override suspend fun seen(): Response<Unit> {
        calls += "seen"
        return Response.success(Unit)
    }

    override suspend fun summary(): OrderSummary {
        calls += "summary"
        return summary.invoke()
    }
}
