package com.whxbill.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "whx_bill_session")

class SessionStore(context: Context) {
    private val dataStore = context.sessionDataStore

    val session: Flow<Session> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            Session(
                accessToken = preferences[Keys.ACCESS_TOKEN].orEmpty(),
                refreshToken = preferences[Keys.REFRESH_TOKEN].orEmpty(),
                userId = preferences[Keys.USER_ID] ?: 0,
                username = preferences[Keys.USERNAME].orEmpty(),
                nickname = preferences[Keys.NICKNAME].orEmpty()
            )
        }

    suspend fun save(login: LoginResponse) {
        dataStore.edit { preferences ->
            preferences[Keys.ACCESS_TOKEN] = login.accessToken
            preferences[Keys.REFRESH_TOKEN] = login.refreshToken
            preferences[Keys.USER_ID] = login.userId
            preferences[Keys.USERNAME] = login.username
            preferences[Keys.NICKNAME] = login.nickname.orEmpty()
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val NICKNAME = stringPreferencesKey("nickname")
    }
}
