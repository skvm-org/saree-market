package com.sareemarket.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.sareemarket.app.data.Role
import com.sareemarket.app.data.Session
import com.sareemarket.app.ui.components.SecondaryButton
import com.sareemarket.app.ui.reseller.ResellerNavHost
import com.sareemarket.app.ui.seller.SellerNavHost
import com.sareemarket.app.ui.theme.Spacing

/** Placeholder landing screen per role; the real seller and reseller screens arrive in later slices. */
@Composable
fun HomeScreen(session: Session, onLogout: () -> Unit) {
    if (session.role == Role.SELLER) {
        SellerNavHost(session, onLogout)
        return
    }
    if (session.role == Role.RESELLER) {
        ResellerNavHost(onLogout)
        return
    }
    val (title, note) = when (session.role) {
        Role.SELLER -> "" to ""
        Role.RESELLER -> "" to ""
        Role.ADMIN -> "Admin" to "Admin screens come with the web app. Use the API for now."
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(Spacing.margin),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        ) {
            Text(title, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primaryContainer)
            Text("Signed in as ${session.name}", style = MaterialTheme.typography.bodyLarge)
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            SecondaryButton(text = "Log out", onClick = onLogout, modifier = Modifier.padding(top = Spacing.md))
        }
    }
}
