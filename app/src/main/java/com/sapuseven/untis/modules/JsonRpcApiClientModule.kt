package com.sapuseven.untis.modules

import com.sapuseven.untis.api.client.AbsenceApi
import com.sapuseven.untis.api.client.ClassRegApi
import com.sapuseven.untis.api.client.MessagesApi
import com.sapuseven.untis.api.client.OfficeHoursApi
import com.sapuseven.untis.api.client.SchoolSearchApi
import com.sapuseven.untis.api.client.TimetableApi
import com.sapuseven.untis.api.client.UserDataApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JsonRpcApiClientModule {
	@Provides
	@Singleton
	fun provideHttpClientEngineFactory(): HttpClientEngineFactory<*> = CIO

	@Provides
	@Singleton
	@Named("json")
	fun provideJsonHttpClient(httpClientEngineFactory: HttpClientEngineFactory<*>): HttpClient =
		HttpClient(httpClientEngineFactory.create()) {
			install(ContentNegotiation) {
				json()
			}
			// Without timeouts a stalled connection (network switch, black-holed TCP session,
			// unresponsive server) keeps the request - and everything waiting on it, like the
			// cached bearer token - suspended forever. The messages screen then shows its cached
			// list while every request hangs.
			install(HttpTimeout) {
				requestTimeoutMillis = 30_000
				connectTimeoutMillis = 15_000
				socketTimeoutMillis = 30_000
			}
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 2)
				retryOnException(maxRetries = 2)
				exponentialDelay()
			}
			expectSuccess = true
		}

	@Provides
	@Singleton
	@Named("download")
	fun provideDownloadHttpClient(httpClientEngineFactory: HttpClientEngineFactory<*>): HttpClient =
		HttpClient(httpClientEngineFactory.create()) {
			install(HttpTimeout) {
				requestTimeoutMillis = 60_000
				connectTimeoutMillis = 15_000
				socketTimeoutMillis = 30_000
			}
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 2)
				retryOnException(maxRetries = 2)
				exponentialDelay()
			}
			expectSuccess = true
		}

	/**
	 * Timeouts for the JSON-RPC API clients.
	 *
	 * The clients are created without any timeout by default: a connection that stalls silently
	 * (network switch, black-holed TCP session, unresponsive server) then keeps the request
	 * suspended forever. The auth token request in particular is executed while holding a lock,
	 * so one hanging call blocks every following request of the app - the messages list is still
	 * shown from the cache, but nothing opens anymore until the app is restarted.
	 */
	private fun HttpClientConfig<*>.withTimeouts() {
		install(HttpTimeout) {
			requestTimeoutMillis = 30_000
			connectTimeoutMillis = 15_000
			socketTimeoutMillis = 30_000
		}
		install(HttpRequestRetry) {
			retryOnServerErrors(maxRetries = 2)
			// `retryOnException` does not retry timeouts (Ktor 3.1.3, `retryOnTimeout = false`),
			// so a dead connection fails after one request timeout instead of three.
			retryOnException(maxRetries = 2)
			exponentialDelay()
		}
	}

	@Provides
	@Singleton
	fun provideSchoolSearchApi(engineFactory: HttpClientEngineFactory<*>): SchoolSearchApi =
		SchoolSearchApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideUserDataApi(engineFactory: HttpClientEngineFactory<*>): UserDataApi =
		UserDataApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideTimetableApi(engineFactory: HttpClientEngineFactory<*>): TimetableApi =
		TimetableApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideMessagesApi(engineFactory: HttpClientEngineFactory<*>): MessagesApi =
		MessagesApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideClassRegApi(engineFactory: HttpClientEngineFactory<*>): ClassRegApi =
		ClassRegApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideAbsenceApi(engineFactory: HttpClientEngineFactory<*>): AbsenceApi =
		AbsenceApi(engineFactory, config = { it.withTimeouts() })

	@Provides
	@Singleton
	fun provideOfficeHoursApi(engineFactory: HttpClientEngineFactory<*>): OfficeHoursApi =
		OfficeHoursApi(engineFactory, config = { it.withTimeouts() })
}
