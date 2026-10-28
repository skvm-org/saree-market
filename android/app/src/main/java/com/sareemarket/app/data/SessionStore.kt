package com.sareemarket.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SessionStore {
    /** The signed-in session, or null when logged out. */
    val session: Flow<Session?>
    suspend fun save(session: Session)
    suspend fun clear()
}

/** A role saved by an older app version: resellers are customers now. Anything unknown means logged out. */
internal fun roleFromStored(value: String?): Role? = when (value) {
    null -> null
    "RESELLER" -> Role.CUSTOMER
    else -> runCatching { Role.valueOf(value) }.getOrNull()
}

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class DataStoreSessionStore(context: Context) : SessionStore {
    private val store = context.sessionDataStore

    override val session: Flow<Session?> = store.data.map { p ->
        val token = p[TOKEN] ?: return@map null
        Session(
            token = token,
            userId = p[USER_ID] ?: return@map null,
            name = p[NAME] ?: return@map null,
            phone = p[PHONE] ?: return@map null,
            role = roleFromStored(p[ROLE]) ?: return@map null,
        )
    }

    override suspend fun save(session: Session) {
        store.edit {
            it[TOKEN] = session.token
            it[USER_ID] = session.userId
            it[NAME] = session.name
            it[PHONE] = session.phone
            it[ROLE] = session.role.name
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private companion object {
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val NAME = stringPreferencesKey("name")
        val PHONE = stringPreferencesKey("phone")
        val ROLE = stringPreferencesKey("role")
    }
}
