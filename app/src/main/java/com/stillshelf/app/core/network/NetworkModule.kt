package com.stillshelf.app.core.network

import com.stillshelf.app.core.diagnostics.DiagnosticLogManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        diagnosticLogManager: DiagnosticLogManager
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                val method = request.method
                try {
                    val response = chain.proceed(request)
                    if (!response.isSuccessful) {
                        diagnosticLogManager.logNetworkError(
                            tag = "OkHttp",
                            errorType = "http_${response.code}",
                            method = method,
                            httpStatusCode = response.code
                        )
                    }
                    response
                } catch (throwable: Throwable) {
                    diagnosticLogManager.logNetworkError(
                        tag = "OkHttp",
                        errorType = throwable::class.java.simpleName.ifBlank { "network_exception" },
                        method = method,
                        throwable = throwable
                    )
                    throw throwable
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
