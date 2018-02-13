package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.BookMatch
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.index.reindex.DeleteByQueryAction
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * repository for accessing the book matches stored in elasticsearch
 */
@Component
class BookMatchRepository(@Autowired val client: Client, @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = BookMatch::class.java.simpleName.toLowerCase()
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
                                        "url": { "type": "keyword" },
                                        "book": { "type": "keyword" },
                                        "blogUrl": { "type": "keyword" },
                                        "byIsbn": { "type": "boolean" },
                                        "finished": { "type": "boolean" }
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

    fun findAll(): List<BookMatch> {
        val result = mutableListOf<BookMatch>()
        chunk { bookMatches -> result.addAll(bookMatches) }
        return result
    }

    /**
     * loads chunk by chunk of book matches from elasticsearch
     * @param callback will be executed for every chunk
     */
    fun chunk(callback: (books: List<BookMatch>) -> Unit) {
        var scrollResp = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .addSort("dnbId", SortOrder.ASC)
                .setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS))
                .setQuery(QueryBuilders.matchAllQuery())
                .setSize(CHUNK_SIZE)
                .execute().actionGet()
        do {
            val chunk = mutableListOf<BookMatch>()
            for (hit in scrollResp.hits.hits) {
                val bookMatch = objectMapper.readValue<BookMatch>(hit.sourceAsString, BookMatch::class.java)
                chunk.add(bookMatch)
            }
            callback(chunk)

            scrollResp = client.prepareSearchScroll(scrollResp.scrollId).setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS)).execute().actionGet()
        } while (scrollResp.hits.hits.isNotEmpty())
    }

    fun save(bookMatches: List<BookMatch>) {
        val bulkRequest = client.prepareBulk()
        for (bookMatch in bookMatches) {
            bulkRequest.add(client.prepareIndex(INDEX, TYPE)
                    .setSource(objectMapper.writeValueAsString(bookMatch), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving bookMatches: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun deleteAllFinished() {
        DeleteByQueryAction.INSTANCE.newRequestBuilder(client)
                .filter(QueryBuilders.queryStringQuery("_type:$TYPE AND finished:true"))
                .source(INDEX)
                .abortOnVersionConflict(false)
                .get()
    }

    fun deleteAllNonFinished() {
        DeleteByQueryAction.INSTANCE.newRequestBuilder(client)
                .filter(QueryBuilders.queryStringQuery("_type:$TYPE AND finished:false"))
                .source(INDEX)
                .abortOnVersionConflict(false)
                .get()
    }

}