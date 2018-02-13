package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.BookInfo
import de.aditu.bookscan.model.BookInfoToSave
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * repository for accessing the book infos stored in elasticsearch
 */
@Component
class BookInfoRepository(@Autowired val client: Client,
                         @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = BookInfo::class.java.simpleName.toLowerCase()
        private val MAX_RESULT_WINDOW = 1_000_000
        private val CHUNK_SIZE = 10_000
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
                                        "isbn": { "type": "keyword" },
                                        "smallThumbnail": { "type": "keyword" },
                                        "bigThumbnail": { "type": "keyword" },
                                        "categories": { "type": "keyword" }
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

    /**
     * loads chunk by chunk of book infos from elasticsearch
     * @param chunkSize the size of one single chunk (per default 10000 book infos)
     * @param callback will be executed for every chunk
     */
    fun chunk(chunkSize: Int = CHUNK_SIZE, callback: (bookInfosToSave: List<BookInfoToSave>) -> Unit) {
        var scrollResp = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS))
                .setQuery(matchAllQuery())
                .setSize(chunkSize)
                .execute().actionGet()
        do {
            val chunk = mutableListOf<BookInfoToSave>()
            for (hit in scrollResp.hits.hits) {
                val bookInfoToSave = objectMapper.readValue<BookInfoToSave>(hit.sourceAsString, BookInfoToSave::class.java)
                chunk.add(bookInfoToSave)
            }
            callback(chunk)

            scrollResp = client.prepareSearchScroll(scrollResp.scrollId).setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS)).execute().actionGet()
        } while (scrollResp.hits.hits.isNotEmpty())
    }

    fun save(bookInfos: List<BookInfo>) {
        val bulkRequest = client.prepareBulk()
        for (bookInfo in bookInfos) {
            val bookInfoToSave = BookInfoToSave(bookInfo)
            bulkRequest.add(client.prepareIndex(INDEX, TYPE, bookInfo.isbn)
                    .setSource(objectMapper.writeValueAsString(bookInfoToSave), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving bookinfos: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun saveBookInfoToSave(bookInfos: List<BookInfoToSave>) {
        val bulkRequest = client.prepareBulk()
        for (bookInfo in bookInfos) {
            bulkRequest.add(client.prepareIndex(INDEX, TYPE, bookInfo.isbn)
                    .setSource(objectMapper.writeValueAsString(bookInfo), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving bookinfos: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun exists(isbns: List<String>): Map<String, Boolean> {
        val request = client.prepareMultiGet()
        for (isbn in isbns) {
            request.add(INDEX, TYPE, isbn)
        }

        val response = request.get()
        val result = mutableMapOf<String, Boolean>()
        var i = 0
        for (itemResponse in response) {
            result.put(isbns[i++], itemResponse.response.isExists)
        }
        return result
    }

    fun findAllAssociatedByIsbn(): Map<String, BookInfoToSave> {
        val result = mutableMapOf<String, BookInfoToSave>()
        chunk { bookInfos ->
            for (bookInfoToSave in bookInfos) {
                result.put(bookInfoToSave.isbn, bookInfoToSave)
            }
        }
        return result
    }
}