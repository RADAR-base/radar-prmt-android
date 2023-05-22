package org.radarcns.detail

import android.annotation.SuppressLint
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONArray
import org.radarbase.producer.rest.RestClient
import org.slf4j.LoggerFactory
import java.io.IOException

class GithubAssetClient(client: OkHttpClient? = null) {
    private val client: OkHttpClient = client ?: RestClient.global().httpClientBuilder().build()

    fun retrieveLatestAsset(url: String, handler: (OnlineAsset) -> Unit) {
        val request = okhttp3.Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                logger.error("Failed to get latest release.", e)
            }

            @SuppressLint("UnspecifiedImmutableFlag", "DEPRECATION")
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.use {
                    response.body?.string() ?: throw IOException("Missing response body")
                }
                if (!response.isSuccessful) throw IOException("Unexpected code ${response.code}: $responseBody")

                val asset = parseGithubAsset(responseBody) ?: return
                handler(asset)
            }
        })
    }

    private fun parseGithubAsset(response: String): OnlineAsset? {
        return try {
            val responseArray = JSONArray(response).takeIf { it.length() > 0 } ?: return null
            val latestRelease = responseArray.optJSONObject(0) ?: return null
            val browserDownloadUrl = latestRelease.optJSONArray("assets")
                ?.takeIf { it.length() > 0 }
                ?.optJSONObject(0)
                ?.optString("browser_download_url")
                ?.takeIf { it != "" }
                ?: return null
            val tagName = latestRelease.optString("tag_name")
                .takeIf { it != "" }
                ?: return null
            OnlineAsset(tagName, browserDownloadUrl)
        } catch (t: Throwable) {
            logger.error("Could not parse malformed JSON: \"{}\"", response, t)
            null
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoginActivityImpl::class.java)
    }
}
