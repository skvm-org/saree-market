package com.sareemarket.app.ui.reseller

import com.sareemarket.app.data.CatalogQuery
import com.sareemarket.app.data.PriceBand
import com.sareemarket.app.data.ReferenceData
import com.sareemarket.app.data.SellerListItem
import com.sareemarket.app.ui.formatRupees

/** One filter that is switched on, shown as a chip the reseller can tap away. [clear] returns the query without it. */
data class ActiveFilter(val key: String, val label: String, val clear: (CatalogQuery) -> CatalogQuery)

/** The filters currently applied, in the order the filter sheet lists them. The keyword and the sort are not filters. */
fun activeFilters(query: CatalogQuery, reference: ReferenceData?, sellers: List<SellerListItem>): List<ActiveFilter> = buildList {
    query.typeIds.forEach { id ->
        val name = reference?.sareeTypes?.firstOrNull { it.id == id }?.name ?: return@forEach
        add(ActiveFilter("type-$id", "Type: $name") { it.copy(typeIds = it.typeIds - id) })
    }
    query.fabricIds.forEach { id ->
        val name = reference?.fabrics?.firstOrNull { it.id == id }?.name ?: return@forEach
        add(ActiveFilter("fabric-$id", "Fabric: $name") { it.copy(fabricIds = it.fabricIds - id) })
    }
    if (query.minPrice != null || query.maxPrice != null) {
        val band = PriceBand.entries.firstOrNull { it.min == query.minPrice && it.max == query.maxPrice }
        val label = band?.label ?: when {
            query.minPrice != null && query.maxPrice != null -> "${formatRupees(query.minPrice)}–${formatRupees(query.maxPrice)}"
            query.minPrice != null -> "From ${formatRupees(query.minPrice)}"
            else -> "Up to ${formatRupees(query.maxPrice!!)}"
        }
        add(ActiveFilter("price", label) { it.copy(minPrice = null, maxPrice = null) })
    }
    if (query.colour.isNotBlank()) add(ActiveFilter("colour", "Colour: ${query.colour.trim()}") { it.copy(colour = "") })
    query.sellerId?.let { id ->
        val shop = sellers.firstOrNull { it.id == id }?.shopName ?: return@let
        add(ActiveFilter("seller", "Seller: $shop") { it.copy(sellerId = null) })
    }
    query.sinceDays?.let { days -> add(ActiveFilter("since", "Last $days days") { it.copy(sinceDays = null) }) }
    if (query.includeSoldOut) add(ActiveFilter("sold-out", "Including sold out") { it.copy(includeSoldOut = false) })
}
