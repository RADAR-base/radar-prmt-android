package org.radarbase.android.auth

import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.util.*

class SourceType(val id: Int, val producer: String, val model: String,
                 val catalogVersion: String, val hasDynamicRegistration: Boolean) {

    @Throws(JSONException::class)
    constructor(jsonString: String) : this(JSONObject(jsonString)) {
        logger.debug("Creating source type from {}", jsonString)
    }

    @Throws(JSONException::class)
    constructor(json: JSONObject) : this(
            json.getInt("sourceTypeId"),
            json.getString("sourceTypeProducer"),
            json.getString("sourceTypeModel"),
            json.getString("sourceTypeCatalogVersion"),
            json.optBoolean("dynamicRegistration", false))

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val type = other as SourceType
        return (id == type.id
                && producer == type.producer
                && model == type.model
                && catalogVersion == type.catalogVersion)
    }

    override fun hashCode(): Int {
        return Objects.hash(id)
    }

    override fun toString(): String {
        return ("SourceType{"
                + "id='" + id + '\''.toString()
                + ", producer='" + producer + '\''.toString()
                + ", model='" + model + '\''.toString()
                + ", catalogVersion='" + catalogVersion + '\''.toString()
                + ", dynamicRegistration=" + hasDynamicRegistration
                + '}'.toString())
    }

    fun addToJson(json: JSONObject) {
        try {
            json.put("sourceTypeId", id)
            json.put("sourceTypeProducer", producer)
            json.put("sourceTypeModel", model)
            json.put("sourceTypeCatalogVersion", catalogVersion)
            json.put("dynamicRegistration", hasDynamicRegistration)
        } catch (ex: JSONException) {
            throw IllegalStateException("Cannot serialize existing SourceMetadata")
        }
    }

    fun toJsonString(): String {
        try {
            return JSONObject().also { addToJson(it) }.toString()
        } catch (ex: JSONException) {
            throw IllegalStateException("Cannot serialize existing SourceMetadata")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(SourceType::class.java)
    }
}
