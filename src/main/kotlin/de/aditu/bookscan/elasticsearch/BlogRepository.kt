package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.Blog
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import org.elasticsearch.rest.RestStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * repository for accessing the blogs stored in elasticsearch
 */
@Component
class BlogRepository(@Autowired val client: Client, @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = Blog::class.java.simpleName.toLowerCase()
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
                                        "url": { "type": "keyword" },
                                        "scanned": { "type": "date" }
                                    }
                                }
                            }
                        """,
                            XContentType.JSON)
                    .get()
        }
    }

    fun save(blog: Blog) {
        val response = client.prepareIndex(INDEX, TYPE, blog.url)
                .setSource(objectMapper.writeValueAsString(blog), XContentType.JSON)
                .get()

        if (response.status() != RestStatus.CREATED && response.status() != RestStatus.OK) {
            log.error("error saving blog: $response")
        }
    }

    fun findByUrl(url: String): Blog? {
        val response = client.prepareGet(INDEX, TYPE, url).get()
        if (!response.isExists || response.isSourceEmpty) {
            return null
        }
        return objectMapper.readValue<Blog>(response.sourceAsString, Blog::class.java)
    }

    fun count() = client.prepareSearch(INDEX)
                        .setTypes(TYPE)
                        .setSize(0)
                        .setQuery(matchAllQuery()).get().hits.getTotalHits()
}