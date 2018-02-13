package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.BookMatchMerge
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * repository for accessing the book matches with extended informations stored in elasticsearch
 */
@Component
class BookMatchMergeRepository(@Autowired val client: Client, @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = BookMatchMerge::class.java.simpleName.toLowerCase()
        private val MAX_RESULT_WINDOW = 1_000_000
        val CHUNK_SIZE = 10_000
        private val SCROLL_TIME_IN_HOURS = 48L
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
                                        "murl": { "type": "keyword" },

                                        "mbook": {
                                            "type": "nested",
                                            "properties": {
                                                "dnbId": { "type": "keyword" },
                                                "title": { "type": "keyword" },
                                                "authors": {
                                                    "type": "nested",
                                                    "properties": {
                                                        "id": { "type": "keyword" },
                                                        "surname": { "type": "keyword" },
                                                        "forename": { "type": "keyword" },
                                                        "type": { "type": "keyword" }
                                                    }
                                                },
                                                "publisher": { "type": "keyword" },
                                                "issued": { "type": "keyword" },
                                                "isbn10": { "type": "keyword" },
                                                "isbn13": { "type": "keyword" }
                                            }
                                        },

                                        "mbookInfo": {
                                            "type": "nested",
                                            "properties": {
                                                "isbn": { "type": "keyword" },
                                                "smallThumbnail": { "type": "keyword" },
                                                "bigThumbnail": { "type": "keyword" },
                                                "categories": { "type": "keyword" }
                                            }
                                        },

                                        "mblogArticle": {
                                            "type": "nested",
                                            "properties": {
                                                "url": { "type": "keyword" },
                                                "blogUrl": { "type": "keyword" },
                                                "date": { "type": "date" },
                                                "title": { "type": "keyword" },
                                                "content": { "type": "keyword" }
                                            }
                                        },

                                        "mblogUrl": { "type": "keyword" },
                                        "mbyIsbn": { "type": "boolean" }
                                    }
                                }
                            }
                        """,
                            XContentType.JSON)
                    .setSettings(Settings.builder()
                            .put("max_result_window", MAX_RESULT_WINDOW)
                    )
                    .get()
        }
    }

    fun findAll(): List<BookMatchMerge> {
        val result = mutableListOf<BookMatchMerge>()
        chunk { bookMatches -> result.addAll(bookMatches) }
        return result
    }

    fun save(bookMatches: List<BookMatchMerge>) {
        val bulkRequest = client.prepareBulk()
        for (bookMatch in bookMatches) {
            bulkRequest.add(client.prepareIndex(INDEX, TYPE)
                    .setSource(objectMapper.writeValueAsString(bookMatch), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving bookMatchMerges: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun findAllUrls(): Map<String, Boolean> {
        val associated = mutableMapOf<String, Boolean>()
        chunk { bookMatches ->
            for (bookMatch in bookMatches) {
                associated.put(bookMatch.murl, true)
            }
        }
        return associated
    }

    /**
     * loads chunk by chunk of book matches from elasticsearch
     * @param callback will be executed for every chunk
     */
    private fun chunk(callback: (books: List<BookMatchMerge>) -> Unit) {
        var scrollResp = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS))
                .setQuery(QueryBuilders.matchAllQuery())
                .setSize(CHUNK_SIZE)
                .execute().actionGet()
        do {
            val chunk = mutableListOf<BookMatchMerge>()
            for (hit in scrollResp.hits.hits) {
                val bookMatch = objectMapper.readValue<BookMatchMerge>(hit.sourceAsString, BookMatchMerge::class.java)
                chunk.add(bookMatch)
            }
            callback(chunk)

            scrollResp = client.prepareSearchScroll(scrollResp.scrollId).setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS)).execute().actionGet()
        } while (scrollResp.hits.hits.isNotEmpty())
    }
}