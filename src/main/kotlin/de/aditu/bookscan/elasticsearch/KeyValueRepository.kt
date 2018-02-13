package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.KeyValue
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * repository for accessing the key values (settings) stored in elasticsearch
 */
@Component
class KeyValueRepository(@Autowired val client: Client, @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = KeyValue::class.java.simpleName.toLowerCase()
    }

    init {
        val exists = client.admin().indices()
                .prepareExists(INDEX)
                .execute().actionGet().isExists

        if (!exists) {
            client.admin().indices().prepareCreate(INDEX)
                    .addMapping(
                            TYPE,
                            """
                            {
                                "$TYPE": {
                                    "properties": {
                                        "key": { "type": "keyword" },
                                        "value": { "type": "keyword" }
                                    }
                                }
                            }
                        """,
                            XContentType.JSON)
                    .get()
        }
    }

    fun save(key: String, value: String) {
        deleteByKey(key)

        val response = client.prepareIndex(INDEX, TYPE, key)
                .setSource(objectMapper.writeValueAsString(KeyValue(key, value)), XContentType.JSON)
                .get()

        if (response.status() != RestStatus.CREATED && response.status() != RestStatus.OK) {
            log.error("error saving key value: $response")
        }
    }

    fun load(key: String, default: String): String {
        val keyValue = findByKey(key)
        return keyValue?.value ?: default
    }

    private fun findByKey(key: String): KeyValue? {
        val response = client.prepareGet(INDEX, TYPE, key).get()
        if (!response.isExists || response.isSourceEmpty) {
            return null
        }
        return objectMapper.readValue<KeyValue>(response.sourceAsString, KeyValue::class.java)
    }

    private fun deleteByKey(key: String) {
        client.prepareDelete(INDEX, TYPE, key).get()
    }
}