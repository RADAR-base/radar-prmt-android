package org.radarbase.android.auth.portal

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.radarbase.android.auth.AppAuthState
import org.radarbase.android.auth.SourceMetadata
import org.radarbase.android.auth.SourceType
import org.radarbase.config.ServerConfig
import java.io.IOException
import java.util.*

@Suppress("SuspiciousCollectionReassignment")
class ManagementPortalClientTest {
    @Test
    @Throws(Exception::class)
    fun parseSources() {
        val `object` = JSONObject(EXAMPLE_REQUEST)
        val project = `object`.getJSONObject("project")
        val sources = `object`.getJSONArray("sources")

        val sourceTypes = GetSubjectParser.parseSourceTypes(project)
        val sourceList = GetSubjectParser.parseSources(sourceTypes, sources)

        val expected = SourceMetadata().apply {
            type = SourceType(0, "p", "m", "v", true)
            sourceId = "i"
            expectedSourceName = "e"
            sourceName = "s"
            attributes += Pair("k", "v")
        }

        assertEquals(listOf(expected), sourceList)
    }

    @Test
    @Throws(Exception::class)
    fun parseEmptySources() {
        val `object` = JSONObject(EXAMPLE_REQUEST)
        val project = `object`.getJSONObject("project")

        val sourceTypes = GetSubjectParser.parseSourceTypes(project)

        val sources = JSONArray()
        val sourceList = GetSubjectParser.parseSources(sourceTypes, sources)

        assertEquals(emptyList<SourceMetadata>(), sourceList)
    }


    @Test
    @Throws(Exception::class)
    fun parseNonDynamicSources() {
        val sourceTypes = listOf(SourceType(0, "p", "m", "v", false))

        val sources = JSONArray()
        val sourceList = GetSubjectParser.parseSources(sourceTypes, sources)

        assertEquals(emptyList<Any>(), sourceList)
    }

    @Test
    @Throws(Exception::class)
    fun parseProjectId() {
        val `object` = JSONObject(EXAMPLE_REQUEST)
        val project = `object`.getJSONObject("project")
        assertEquals("proj-name", GetSubjectParser.parseProjectId(project))
    }

    @Test
    @Throws(Exception::class)
    fun parseUserId() {
        val `object` = JSONObject(EXAMPLE_REQUEST)
        assertEquals("sub-1", GetSubjectParser.parseUserId(`object`))
    }

    @Test
    @Throws(IOException::class)
    fun requestSubject() {
        MockWebServer().use { server ->

            // Schedule some responses.
            server.enqueue(MockResponse()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .setBody(EXAMPLE_REQUEST))

            // Start the server.
            server.start()

            val serverConfig = ServerConfig(server.url("/").toUrl())

            val client = ManagementPortalClient(serverConfig, "pRMT", "")
            val authState = AppAuthState {
                userId = "sub-1"
            }
            val retAuthState = client.getSubject(authState, GetSubjectParser(authState))

            val expected = SourceMetadata().apply {
                type = SourceType(0, "p", "m", "v", true)
                sourceId = "i"
                sourceName = "s"
                expectedSourceName = "e"
                attributes += Pair("k", "v")
            }

            assertEquals(listOf(expected), retAuthState.sourceMetadata)
            assertEquals("proj-name", retAuthState.projectId)
            assertEquals("sub-1", retAuthState.userId)
        }
    }

    @Test
    @Throws(JSONException::class)
    fun sourceRegistrationBody() {
        val source = SourceMetadata().apply {
            type = SourceType(0, "p", "m", "v", true)
            sourceName = "something"
            attributes += Pair("firmware", "0.11")
        }

        val body = ManagementPortalClient.sourceRegistrationBody(source).toString()
        val `object` = JSONObject(body)
        assertEquals("something", `object`.getString("sourceName"))
        assertEquals(0, `object`.getInt("sourceTypeId").toLong())
        val attr = `object`.getJSONObject("attributes")
        assertEquals(3, `object`.names().length().toLong())
        assertEquals("0.11", attr.getString("firmware"))
        assertEquals(1, attr.names().length().toLong())
    }

    @Test
    @Throws(JSONException::class)
    fun sourceRegistrationBodyWithSourceNameSanitizing() {
        val source = SourceMetadata().apply {
            type = SourceType(0, "p", "m", "v", true)
            sourceName = "something(With)_others+"
        }

        val body = ManagementPortalClient.sourceRegistrationBody(source).toString()
        val `object` = JSONObject(body)
        assertEquals("something-With-_others-", `object`.getString("sourceName"))
        assertEquals(0, `object`.getInt("sourceTypeId").toLong())
        assertEquals(2, `object`.names().length().toLong())
    }

    @Test
    @Throws(JSONException::class)
    fun parseSourceRegistration() {
        val source = SourceMetadata().apply {
            type = SourceType(0, "p", "m", "v", true)
            sourceName = "something"
        }
        val response = "{\"sourceName\": \"something_18131\", \"sourceId\": \"uuid-abcdef\", \"deviceTypeId\": 0, \"attributes\":{\"firmware\":\"0.11\"}, \"expectedSourceName\": \"abc\"}"
        ManagementPortalClient.parseSourceRegistration(response, source)
        assertEquals("something_18131", source.sourceName)
        assertEquals("uuid-abcdef", source.sourceId)
        assertEquals(Collections.singletonMap("firmware", "0.11"), source.attributes)
        assertEquals("abc", source.expectedSourceName)
    }

    companion object {
        private const val EXAMPLE_REQUEST = ("{\n"
                + "  \"attributes\": [\n"
                + "    {\n"
                + "      \"key\": \"string\",\n"
                + "      \"value\": \"string\"\n"
                + "    }\n"
                + "  ],\n"
                + "  \"createdBy\": \"string\",\n"
                + "  \"createdDate\": \"2017-10-03T14:07:56.708Z\",\n"
                + "  \"externalId\": \"string\",\n"
                + "  \"externalLink\": \"string\",\n"
                + "  \"id\": 0,\n"
                + "  \"lastModifiedBy\": \"string\",\n"
                + "  \"lastModifiedDate\": \"2017-10-03T14:07:56.708Z\",\n"
                + "  \"login\": \"sub-1\",\n"
                + "  \"project\": {\n"
                + "    \"attributes\": [\n"
                + "      {\n"
                + "        \"key\": \"string\",\n"
                + "        \"value\": \"string\"\n"
                + "      }\n"
                + "    ],\n"
                + "    \"description\": \"string\",\n"
                + "    \"sourceTypes\": [\n"
                + "      {\n"
                + "        \"canRegisterDynamically\": true,\n"
                + "        \"catalogVersion\": \"v\",\n"
                + "        \"model\": \"m\",\n"
                + "        \"producer\": \"p\",\n"
                + "        \"id\": 0,\n"
                + "        \"sensorData\": [\n"
                + "          {\n"
                + "            \"dataClass\": \"RAW\",\n"
                + "            \"dataType\": \"RAW\",\n"
                + "            \"enabled\": true,\n"
                + "            \"frequency\": \"string\",\n"
                + "            \"id\": 0,\n"
                + "            \"keySchema\": \"string\",\n"
                + "            \"provider\": \"string\",\n"
                + "            \"sensorName\": \"string\",\n"
                + "            \"topic\": \"string\",\n"
                + "            \"unit\": \"string\",\n"
                + "            \"valueSchema\": \"string\"\n"
                + "          }\n"
                + "        ],\n"
                + "        \"sourceType\": \"ACTIVE\"\n"
                + "      }\n"
                + "    ],\n"
                + "    \"endDate\": \"2017-10-03T14:07:56.708Z\",\n"
                + "    \"id\": 0,\n"
                + "    \"location\": \"string\",\n"
                + "    \"organization\": \"string\",\n"
                + "    \"projectAdmin\": 0,\n"
                + "    \"projectName\": \"proj-name\",\n"
                + "    \"projectStatus\": \"PLANNING\",\n"
                + "    \"startDate\": \"2017-10-03T14:07:56.708Z\"\n"
                + "  },\n"
                + "  \"sources\": [\n"
                + "    {\n"
                + "      \"assigned\": true,\n"
                + "      \"sourceTypeId\": 0,\n"
                + "      \"sourceTypeProducer\": \"dp\",\n"
                + "      \"sourceTypeModel\": \"dm\",\n"
                + "      \"sourceTypeCatalogVersion\": \"dv\",\n"
                + "      \"expectedSourceName\": \"e\",\n"
                + "      \"id\": 0,\n"
                + "      \"sourceId\": \"i\",\n"
                + "      \"sourceName\": \"s\",\n"
                + "      \"attributes\": {\"k\": \"v\"}\n"
                + "    }\n"
                + "  ],\n"
                + "  \"status\": \"DEACTIVATED\"\n"
                + "}")
    }
}
