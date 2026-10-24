package com.sareemarket.app.di

import android.content.Context
import com.sareemarket.app.BuildConfig
import com.sareemarket.app.data.AuthApi
import com.sareemarket.app.data.AuthInterceptor
import com.sareemarket.app.data.CollectionsApi
import com.sareemarket.app.data.ContentResolverPhotoSource
import com.sareemarket.app.data.PhotoSource
import com.sareemarket.app.data.DataStoreSessionStore
import com.sareemarket.app.data.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun sessionStore(@ApplicationContext context: Context): SessionStore = DataStoreSessionStore(context)

    @Provides
    @Singleton
    fun okHttpClient(sessionStore: SessionStore): OkHttpClient =
        OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessionStore)).build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun collectionsApi(retrofit: Retrofit): CollectionsApi = retrofit.create(CollectionsApi::class.java)

    @Provides
    @Singleton
    fun photoSource(@ApplicationContext context: Context): PhotoSource = ContentResolverPhotoSource(context.contentResolver)
}
