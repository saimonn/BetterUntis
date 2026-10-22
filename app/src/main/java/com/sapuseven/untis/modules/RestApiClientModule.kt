package com.sapuseven.untis.modules

import com.sapuseven.untis.api.client.UserDataApi
import com.sapuseven.untis.api.rest.MessagesApi
import com.sapuseven.untis.data.repository.UserRepository
import com.sapuseven.untis.exceptions.UntisRestApiException
import com.sapuseven.untis.model.rest.ErrorResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import kotlin.reflect.KProperty

@Module
@InstallIn(SingletonComponent::class)
object RestApiClientModule {
	@Provides
	fun provideMessagesApiFactory(
		userRepository: UserRepository,
		userDataApi: UserDataApi,
		httpClientEngineFactory: HttpClientEngineFactory<*>
	): MessagesApiFactory =
		DefaultMessagesApiFactory(userRepository, userDataApi, httpClientEngineFactory)
}

interface MessagesApiFactory {
	suspend fun create(): MessagesApi
}

class DefaultMessagesApiFactory @Inject constructor(
	private val userRepository: UserRepository,
	private val userDataApi: UserDataApi,
	private val httpClientEngineFactory: HttpClientEngineFactory<*>
) : MessagesApiFactory {
	private data class CachedToken(val userId: Long, val value: String, val fetchedAt: Long)

	val messagesApi by keyedLazy({ userRepository.currentUser!!.id }) {
		MessagesApi(
			baseUrl = userRepository.currentUser!!.restApiUrl.toString(),
			httpClientEngine = httpClientEngineFactory.create()
		) {
			it.expectSuccess = true
			it.install(ContentNegotiation) {
				json()
			}
			it.install(HttpTimeout) {
				requestTimeoutMillis = REQUEST_TIMEOUT_MS
				connectTimeoutMillis = CONNECT_TIMEOUT_MS
			}
			it.install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 2)
				retryOnException(maxRetries = 2)
				exponentialDelay()
			}
			it.HttpResponseValidator {
				handleResponseExceptionWithRequest { exception, _ ->
					val clientException = exception as? ClientRequestException
						?: return@handleResponseExceptionWithRequest

					// The token expired (or was invalidated by a newer login), fetch a new one for
					// the next request instead of failing every request from now on.
					if (clientException.response.status == HttpStatusCode.Unauthorized)
						invalidateToken()

					// The error body is not guaranteed to be an ErrorResponse; keep the original
					// exception in that case instead of masking it with a decoding error.
					val errorResponse = runCatching { clientException.response.body<ErrorResponse>() }.getOrNull()
						?: throw exception

					throw UntisRestApiException(errorResponse)
				}
			}
		}
	}

	@Volatile
	private var cachedToken: CachedToken? = null
	private val tokenMutex = Mutex()

	override suspend fun create(): MessagesApi = messagesApi.apply {
		setBearerToken(token())
	}

	/**
	 * Returns the bearer token of the current user.
	 *
	 * Requesting a token is a full JSON-RPC round trip, so doing it for every REST call made
	 * opening a message with attachments take several seconds. The token is therefore reused for
	 * a short while, which also prevents several parallel calls from starting parallel logins.
	 */
	private suspend fun token(): String = tokenMutex.withLock {
		val user = userRepository.currentUser!!
		val now = System.currentTimeMillis()
		val cached = cachedToken

		if (cached != null && cached.userId == user.id && now - cached.fetchedAt < TOKEN_LIFETIME_MS)
			return cached.value

		val token = userDataApi.getAuthToken(user.jsonRpcApiUrl.toString(), user.user, user.key)
		cachedToken = CachedToken(user.id, token, now)
		return token
	}

	private fun invalidateToken() {
		cachedToken = null
	}

	private companion object {
		/**
		 * Lifetime of a cached token. The server side expiry is not known, so the cached token is
		 * only reused for a short amount of time and replaced when the server rejects it.
		 */
		private const val TOKEN_LIFETIME_MS = 60_000L
		private const val REQUEST_TIMEOUT_MS = 20_000L
		private const val CONNECT_TIMEOUT_MS = 10_000L
	}
}

class KeyedLazy<K, V>(
	private val keyProvider: () -> K,
	private val initializer: (K) -> V
) {
	private var cachedKey: K? = null
	private var cachedValue: V? = null

	operator fun getValue(thisRef: Any?, property: KProperty<*>): V = synchronized(this) {
		val key = keyProvider()
		if (cachedKey != key) {
			cachedValue = initializer(key)
			cachedKey = key
		}
		return cachedValue!!
	}
}

fun <K, V> keyedLazy(keyProvider: () -> K, initializer: (K) -> V): KeyedLazy<K, V> =
	KeyedLazy(keyProvider, initializer)
