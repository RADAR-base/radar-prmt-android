package org.radarbase.android.auth

import org.json.JSONException
import org.json.JSONObject
import org.radarbase.android.RadarService.Companion.sanitizedIds
import org.radarbase.android.util.takeTrimmedIfNotEmpty
import org.radarbase.util.Strings
import org.radarcns.android.auth.AppSource
import java.util.*
import kotlin.collections.HashMap

class SourceMetadata {
    var type: SourceType? = null
    var sourceId: String? = null
    var sourceName: String? = null
    var expectedSourceName: String? = null
        set(value) {
            field = value?.takeTrimmedIfNotEmpty()
                    ?.takeUnless { it == "null" }
        }
    var attributes: Map<String, String> = mapOf()
        set(value) {
            field = HashMap(value)
        }

    constructor()

    constructor(type: SourceType) {
        this.type = type
    }

    @Deprecated("Use direct constructor instead")
    @SuppressWarnings("deprecation")
    @Suppress("DEPRECATION")
    constructor(appSource: AppSource) {
        this.type = SourceType(
                appSource.sourceTypeId.toInt(),
                appSource.sourceTypeProducer,
                appSource.sourceTypeModel,
                appSource.sourceTypeCatalogVersion,
                appSource.hasDynamicRegistration())
        this.sourceId = appSource.sourceId
        this.sourceName = appSource.sourceName
        this.expectedSourceName = appSource.expectedSourceName
        this.attributes = HashMap(appSource.attributes)
    }

    @Throws(JSONException::class)
    constructor(jsonString: String) {
        val json = JSONObject(jsonString)

        this.type = try {
            SourceType(json)
        } catch (ex: JSONException) {
            null
        }
        this.sourceId = json.optString("sourceId", null)
        this.sourceName = json.optString("sourceName", null)
        this.expectedSourceName = json.optString("expectedSourceName", null)

        val attr = HashMap<String, String>()
        val attributesJson = json.optJSONObject("attributes")
        if (attributesJson != null) {
            val it = attributesJson.keys()
            while (it.hasNext()) {
                val key = it.next()
                attr[key] = attributesJson.getString(key)
            }
        }
        this.attributes = attr
    }

    fun deduplicateType(types: MutableCollection<SourceType>) {
        type?.let { t ->
            val storedType = types.find { it.id == t.id }
            if (storedType == null) {
                types += t
            } else {
                type = storedType
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val appSource = other as SourceMetadata
        return (type == appSource.type
                && sourceId == appSource.sourceId
                && sourceName == appSource.sourceName
                && expectedSourceName == appSource.expectedSourceName
                && attributes == appSource.attributes)
    }

    override fun hashCode(): Int {
        return Objects.hash(type, sourceId)
    }

    override fun toString(): String {
        return ("SourceMetadata{"
                + "type=" + type
                + ", sourceId='" + sourceId + '\''.toString()
                + ", sourceName='" + sourceName + '\''.toString()
                + ", expectedSourceName='" + expectedSourceName + '\''.toString()
                + ", attributes=" + attributes + '\''.toString()
                + '}'.toString())
    }

    fun toJsonString(): String {
        try {
            val json = JSONObject()
            type?.addToJson(json)
            json.put("sourceId", sourceId)
            json.put("sourceName", sourceName)
            json.put("expectedSourceName", expectedSourceName)

            val attributeJson = JSONObject()
            for ((key, value) in attributes) {
                attributeJson.put(key, value)
            }
            json.put("attributes", JSONObject().apply {
                attributes.forEach { (k, v) -> put(k, v) }
            })
            return json.toString()
        } catch (ex: JSONException) {
            throw IllegalStateException("Cannot serialize existing SourceMetadata")
        }
    }

    fun matches(vararg names: String?): Boolean {
        val actualNames = names.filterNotNull()
        return expectedSourceName?.split(",".toRegex())?.let { expected ->
            Strings.containsPatterns(sanitizedIds(expected))
                    .any { pattern -> actualNames.any { pattern.matcher(it).find() } }
        } ?: true
    }
}
