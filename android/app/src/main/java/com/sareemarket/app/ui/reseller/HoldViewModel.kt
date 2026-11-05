package com.sareemarket.app.ui.reseller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sareemarket.app.data.Address
import com.sareemarket.app.data.AddressRepository
import com.sareemarket.app.data.AddressSnapshot
import com.sareemarket.app.data.ApiFailure
import com.sareemarket.app.data.CartRepository
import com.sareemarket.app.data.Hold
import com.sareemarket.app.data.OrderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HoldState(
    val hold: Hold? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val utr: String = "",
    /** The screenshot the customer picked, as a URI string. */
    val proof: String? = null,
    val busy: Boolean = false,
    val payError: String? = null,
    /** The two hours ran out (or the hold is gone): nothing can be paid here any more. */
    val expired: Boolean = false,
    /** The payment went through: the screen opens this order, then calls [HoldViewModel.paidHandled]. */
    val paidOrderId: Long? = null,
    /** The customer released the hold: the screen goes back to the cart. */
    val closed: Boolean = false,
    /** The "change address" sheet while it is open. */
    val addressSheet: HoldAddressUi? = null,
) {
    val canPay: Boolean get() = hold != null && utr.length == UTR_LENGTH && !busy && !expired
}

/** The sheet for moving an open hold to another saved address. */
data class HoldAddressUi(
    val addresses: List<Address> = emptyList(),
    val selectedId: Long? = null,
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
)

private const val UTR_LENGTH = 12

private fun Address.isSameAs(s: AddressSnapshot) =
    name == s.name && phone == s.phone && line1 == s.line1 && line2 == s.line2 && city == s.city && state == s.state && pin == s.pin

/** One payment hold: how long is left, whom to pay, and the UTR (plus optional screenshot) that turns it into an order. */
@HiltViewModel
class HoldViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val orders: OrderRepository,
    private val cart: CartRepository,
    private val addresses: AddressRepository,
) : ViewModel() {

    private val holdId: Long = checkNotNull(handle["id"])

    private val _state = MutableStateFlow(HoldState())
    val state: StateFlow<HoldState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            orders.holds().fold(
                onSuccess = { list ->
                    val hold = list.firstOrNull { it.id == holdId }
                    _state.update {
                        if (hold == null) it.copy(loading = false, expired = true, error = "This payment is no longer open. If you sent your UTR, check My orders.")
                        else it.copy(loading = false, hold = hold)
                    }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun onUtr(value: String) = _state.update { it.copy(utr = value.filter(Char::isDigit).take(UTR_LENGTH), payError = null) }

    fun onProof(uri: String?) = _state.update { it.copy(proof = uri) }

    fun markExpired() = _state.update { it.copy(expired = true) }

    fun pay() {
        val s = _state.value
        if (!s.canPay) return
        _state.update { it.copy(busy = true, payError = null) }
        viewModelScope.launch {
            orders.pay(holdId, s.utr, s.proof).fold(
                onSuccess = { order -> _state.update { it.copy(busy = false, paidOrderId = order.id) } },
                onFailure = { e ->
                    val ended = (e as? ApiFailure)?.code == "HOLD_EXPIRED"
                    _state.update { it.copy(busy = false, payError = e.message, expired = it.expired || ended) }
                },
            )
        }
    }

    fun openAddressSheet() {
        if (_state.value.hold == null || _state.value.expired) return
        _state.update { it.copy(addressSheet = HoldAddressUi()) }
        refreshAddresses()
    }

    fun closeAddressSheet() = _state.update { it.copy(addressSheet = null) }

    fun selectAddress(id: Long?) = _state.update { s -> s.copy(addressSheet = s.addressSheet?.copy(selectedId = id, error = null)) }

    /**
     * Looks at the saved addresses again, also when the customer comes back from editing or adding one. The selection stays
     * on the same address; one that was just added is selected; and at first the address the hold already goes to is selected.
     */
    fun refreshAddresses() {
        if (_state.value.addressSheet == null) return
        viewModelScope.launch {
            addresses.list().fold(
                onSuccess = { list ->
                    _state.update { s ->
                        val sheet = s.addressSheet ?: return@update s
                        val keep = sheet.selectedId?.takeIf { id -> list.any { it.id == id } }
                        val added = if (sheet.loading) null else list.filter { a -> sheet.addresses.none { it.id == a.id } }.maxByOrNull { it.id }
                        val current = s.hold?.address?.let { snap -> list.firstOrNull { it.isSameAs(snap) }?.id }
                        s.copy(addressSheet = sheet.copy(addresses = list, selectedId = added?.id ?: keep ?: current, loading = false))
                    }
                },
                onFailure = { e -> _state.update { s -> s.copy(addressSheet = s.addressSheet?.copy(loading = false, error = e.message)) } },
            )
        }
    }

    /** Sends the hold to the selected address. Allowed even for the address it already has, so an edit can be applied. */
    fun applyAddress() {
        val sheet = _state.value.addressSheet ?: return
        val id = sheet.selectedId ?: return
        if (sheet.busy) return
        _state.update { it.copy(addressSheet = sheet.copy(busy = true, error = null)) }
        viewModelScope.launch {
            orders.changeHoldAddress(holdId, id).fold(
                onSuccess = { hold -> _state.update { it.copy(hold = hold, addressSheet = null) } },
                onFailure = { e ->
                    val ended = (e as? ApiFailure)?.code == "HOLD_EXPIRED"
                    _state.update { s -> s.copy(expired = s.expired || ended, addressSheet = s.addressSheet?.copy(busy = false, error = e.message)) }
                },
            )
        }
    }

    fun paidHandled() = _state.update { it.copy(paidOrderId = null) }

    /** Gives the sarees back and returns the customer to the cart. */
    fun cancel() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, payError = null) }
        viewModelScope.launch {
            orders.cancelHold(holdId).fold(
                onSuccess = {
                    cart.refresh()
                    _state.update { it.copy(busy = false, closed = true) }
                },
                onFailure = { e -> _state.update { it.copy(busy = false, payError = e.message) } },
            )
        }
    }
}
