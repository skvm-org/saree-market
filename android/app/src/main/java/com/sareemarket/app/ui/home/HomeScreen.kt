package com.sareemarket.app.ui.home

import androidx.compose.runtime.Composable
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.admin.AdminNavHost
import com.sareemarket.app.ui.reseller.ResellerNavHost
import com.sareemarket.app.ui.seller.SellerNavHost

/** Each role gets its own navigation graph, all inside the same side menu. */
@Composable
fun HomeScreen(session: Session, onLogout: () -> Unit) {
    when (session.role) {
        Role.SELLER -> SellerNavHost(session, onLogout)
        Role.RESELLER -> ResellerNavHost(session, onLogout)
        Role.ADMIN -> AdminNavHost(session, onLogout)
    }
}
