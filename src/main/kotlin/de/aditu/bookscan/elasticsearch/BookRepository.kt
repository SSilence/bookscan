package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.Book
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import org.elasticsearch.search.sort.SortOrder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit


/**
 * repository for accessing the books stored in elasticsearch
 */
@Component
class BookRepository(@Autowired val client: Client,
                     @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = Book::class.java.simpleName.toLowerCase()

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
     * loads chunk by chunk of books from elasticsearch
     * @param chunkSize the size of one single chunk (per default 10000 books)
     * @param callback will be executed for every chunk
     */
    fun chunk(chunkSize: Int = CHUNK_SIZE, callback: (books: List<Book>) -> Unit) {
        var scrollResp = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .addSort("dnbId", SortOrder.ASC)
                .setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS))
                .setQuery(matchAllQuery())
                .setSize(chunkSize)
                .execute().actionGet()
        do {
            val chunk = mutableListOf<Book>()
            for (hit in scrollResp.hits.hits) {
                val book = objectMapper.readValue<Book>(hit.sourceAsString, Book::class.java)
                chunk.add(book)
            }
            callback(chunk)

            scrollResp = client.prepareSearchScroll(scrollResp.scrollId).setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS)).execute().actionGet()
        } while (scrollResp.hits.hits.isNotEmpty())
    }

    fun save(books: List<Book>) {
        val bulkRequest = client.prepareBulk()
        for (book in books) {
            bulkRequest.add(client.prepareIndex(INDEX, TYPE, book.dnbId)
                    .setSource(objectMapper.writeValueAsString(book), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving books: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun delete(books: List<Book>) {
        val bulkRequest = client.prepareBulk()
        for (book in books) {
            bulkRequest.add(client.prepareDelete(INDEX, TYPE, book.dnbId))
        }
        bulkRequest.get()
    }

    fun findByDnbId(dnbIds: List<String>): List<Book> {
        val request = client.prepareMultiGet()
        for (dnbId in dnbIds) {
            request.add(INDEX, TYPE, dnbId)
        }

        val response = request.get()
        val result = mutableListOf<Book>()
        for (itemResponse in response) {
            if (itemResponse.response.isExists) {
                val book = objectMapper.readValue<Book>(itemResponse.response.sourceAsString, Book::class.java)
                result.add(book)
            }
        }
        return result
    }

    fun findAllAssociatedByDnbId(dnbIds: List<String>): Map<String, Book> =
            findByDnbId(dnbIds).filter { it.dnbId != null }.associateBy({ it.dnbId!! }, { it })
}