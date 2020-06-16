package org.radarbase.android.auth.portal

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.AuthStringParser
import org.radarbase.android.auth.SourceMetadata
import org.radarbase.android.util.Parser
import org.radarbase.config.ServerConfig
import org.radarbase.producer.AuthenticationException
import org.radarbase.producer.rest.RestClient
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.MalformedURLException

class ManagementPortalClient(managementPortal: ServerConfig, clientId: String, clientSecret: String, client: RestClient? = null) {
    val client: RestClient = (client?.newBuilder() ?: RestClient.newClient())
            .server(managementPortal)
            .build()

    private val credentials = Credentials.basic(clientId, clientSecret)

    /**
     * Get refresh-token from meta-token url.
     * @param metaTokenUrl current token url
     * @param parser string parser
     * @throws IOException if the management portal could not be reached or it gave an erroneous
     * response.
     */
    @Throws(IOException::class)
    fun getRefreshToken(metaTokenUrl: String, parser: AuthStringParser): AppAuthState {
        val request = client.requestBuilder(metaTokenUrl)
                .header("Accept", APPLICATION_JSON)
                .build()

        logger.debug("Requesting refreshToken with token-url {}", metaTokenUrl)

        return handleRequest(request, parser)
    }

    /**
     * Get subject information from the Management portal. This includes project ID, available
     * source types and assigned sources.
     * @param state current authentication state
     * @throws IOException if the management portal could not be reached or it gave an erroneous
     * response.
     */
    @Throws(IOException::class)
    fun getSubject(state: AppAuthState, parser: AuthStringParser): AppAuthState {
        if (state.userId == null) {
            throw IOException("Authentication state does not contain user ID")
        }
        val request = client.requestBuilder("api/subjects/${state.userId}")
                .headers(state.okHttpHeaders)
                .header("Accept", APPLICATION_JSON)
                .build()

        logger.info("Requesting subject {} with parseHeaders {}", state.userId,
                state.okHttpHeaders)

        return handleRequest(request, parser)
    }

    /** Register a source with the Management Portal.  */
    @Throws(IOException::class, JSONException::class)
    fun registerSource(auth: AppAuthState, source: SourceMetadata): SourceMetadata {
        val bodyString = sourceRegistrationBody(source).toString()
        val body = bodyString.toRequestBody(APPLICATION_JSON_TYPE)

        val request = client.requestBuilder("api/subjects/${auth.userId}/sources")
                .post(body)
                .headers(auth.okHttpHeaders)
                .header("Content-Type", APPLICATION_JSON_UTF8)
                .header("Accept", APPLICATION_JSON)
                .build()

        client.request(request).use { response ->
            when (response.code) {
                401 -> throw AuthenticationException("Authentication failure with the ManagementPortal.")
                403 -> throw UnsupportedOperationException("Not allowed to update source data.")
                404 -> throw IOException("User ${auth.userId} is no longer registered with the ManagementPortal.")
                409 -> throw ConflictException()
                else -> {
                    val responseBody = RestClient.responseBody(response) ?: ""

                    if (!response.isSuccessful) {
                        throw IOException("Cannot complete source registration with the ManagementPortal: $responseBody, using request $bodyString")
                    } else if (responseBody.isEmpty()) {
                        throw IOException("Source registration with the ManagementPortal did not yield result.")
                    } else {
                        parseSourceRegistration(responseBody, source)
                        return source
                    }
                }
            }
        }
    }


    /** Register a source with the Management Portal.  */
    @Throws(IOException::class, JSONException::class)
    fun updateSource(auth: AppAuthState, source: SourceMetadata): SourceMetadata {
        val bodyString = sourceUpdateBody(source).toString()
        val body = bodyString.toRequestBody(APPLICATION_JSON_TYPE)

        val request = client.requestBuilder("api/subjects/${auth.userId}/sources/${source.sourceName}")
                .post(body)
                .headers(auth.okHttpHeaders)
                .header("Content-Type", APPLICATION_JSON_UTF8)
                .header("Accept", APPLICATION_JSON)
                .build()

        client.request(request).use { response ->
            when (response.code) {
                401 -> throw AuthenticationException("Authentication failure with the ManagementPortal.")
                403 -> throw UnsupportedOperationException("Not allowed to update source data.")
                404 -> throw IOException("User ${auth.userId} is no longer registered with the ManagementPortal or the source no longer exists.")
                else -> {
                    val responseBody = RestClient.responseBody(response) ?: ""

                    if (!response.isSuccessful) {
                        throw IOException("Cannot complete source registration with the ManagementPortal: $responseBody, using request $bodyString")
                    } else if (responseBody.isEmpty()) {
                        throw IOException("Source registration with the ManagementPortal did not yield result.")
                    } else {
                        parseSourceRegistration(responseBody, source)
                        return source
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun refreshToken(authState: AppAuthState, parser: AuthStringParser): AppAuthState {
        try {
            val refreshToken = requireNotNull(authState.getAttribute(MP_REFRESH_TOKEN_PROPERTY)) {
                "No refresh token found"
            }

            val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()

            val request = client.requestBuilder("oauth/token")
                    .post(body)
                    .addHeader("Authorization", credentials)
                    .build()

            return handleRequest(request, parser)
        } catch (e: MalformedURLException) {
            throw IllegalStateException("Failed to create request from ManagementPortal url", e)
        }

    }

    @Throws(IOException::class)
    private fun <T> handleRequest(request: Request, parser: Parser<String, T>): T {
        return client.request(request).use { response ->
            val body = RestClient.responseBody(response) ?: ""

            when {
                response.code == 401 -> throw AuthenticationException("QR code is invalid: $body")
                !response.isSuccessful -> throw IOException("Failed to make request; response $body")
                body.isEmpty() -> throw IOException("Response body expected but not found")
                else -> parser.parse(body)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ManagementPortalClient::class.java)

        const val SOURCES_PROPERTY = "org.radarcns.android.auth.portal.ManagementPortalClient.sources"
        const val MP_REFRESH_TOKEN_PROPERTY = "org.radarcns.android.auth.portal.ManagementPortalClient.refreshToken"
        private const val APPLICATION_JSON = "application/json"
        private const val APPLICATION_JSON_UTF8 = "$APPLICATION_JSON; charset=utf-8"
        private val APPLICATION_JSON_TYPE = APPLICATION_JSON_UTF8.toMediaType()
        private val illegalSourceCharacters = "[^_'.@A-Za-z0-9- ]+".toRegex()

        @Throws(JSONException::class)
        internal fun sourceRegistrationBody(source: SourceMetadata): JSONObject {
            val requestBody = JSONObject()

            val typeId = requireNotNull(source.type?.id) { "Cannot register source without a type" }
            requestBody.put("sourceTypeId", typeId)

            source.sourceName?.let {
                val sourceName = it.replace(illegalSourceCharacters, "-")
                requestBody.put("sourceName", sourceName)
                logger.info("Add {} as sourceName", sourceName)
            }
            val sourceAttributes = source.attributes
            if (sourceAttributes.isNotEmpty()) {
                val attrs = JSONObject()
                for ((key, value) in sourceAttributes) {
                    attrs.put(key, value)
                }
                requestBody.put("attributes", attrs)
            }
            return requestBody
        }

        @Throws(JSONException::class)
        internal fun sourceUpdateBody(source: SourceMetadata): JSONObject {
            return JSONObject().apply {
                for ((key, value) in source.attributes) {
                    put(key, value)
                }
            }
        }

        /**
         * Parse the response of a subject/source registration.
         * @param body registration response body
         * @param source existing source to update with server information.
         * @throws JSONException if the provided body is not valid JSON with the correct properties
         */
        @Throws(JSONException::class)
        internal fun parseSourceRegistration(body: String, source: SourceMetadata) {
            logger.debug("Parsing source from {}", body)
            val responseObject = JSONObject(body)
            source.sourceId = responseObject.getString("sourceId")
            source.sourceName = responseObject.optString("sourceName", source.sourceId)
            source.expectedSourceName = responseObject.optString("expectedSourceName", null)
            source.attributes = GetSubjectParser.attributesToMap(
                    responseObject.optJSONObject("attributes"))
        }
    }
}
