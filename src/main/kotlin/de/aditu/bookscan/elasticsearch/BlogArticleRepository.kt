package de.aditu.bookscan.elasticsearch

import com.fasterxml.jackson.databind.ObjectMapper
import de.aditu.bookscan.model.*
import net.sourceforge.isbnhyphenappender.ISBNHyphenAppender
import org.elasticsearch.client.Client
import org.springframework.beans.factory.annotation.Autowired
import org.elasticsearch.common.xcontent.XContentType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import java.util.concurrent.TimeUnit

/**
 * repository for accessing the blog articles stored in elasticsearch
 */
@Component
class BlogArticleRepository(@Autowired val client: Client, @Autowired private val objectMapper: ObjectMapper) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val INDEX = "bookscan"
        private val TYPE = BlogArticle::class.java.simpleName.toLowerCase()

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
                                        "blogUrl": { "type": "keyword" },
                                        "date": { "type": "date" },
                                        "title": { "type": "keyword" },
                                        "content": { "type": "keyword" }
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
     * loads chunk by chunk of blog articles from elasticsearch
     * @param chunkSize the size of one single chunk (per default 10000 blog articles)
     * @param callback will be executed for every chunk
     */
    fun chunk(chunkSize: Int = CHUNK_SIZE, callback: (books: List<BlogArticle>) -> Unit) {
        var scrollResp = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS))
                .setQuery(matchAllQuery())
                .setSize(chunkSize)
                .execute().actionGet()
        do {
            val chunk = mutableListOf<BlogArticle>()
            for (hit in scrollResp.hits.hits) {
                val book = objectMapper.readValue<BlogArticle>(hit.sourceAsString, BlogArticle::class.java)
                chunk.add(book)
            }
            callback(chunk)

            scrollResp = client.prepareSearchScroll(scrollResp.scrollId).setScroll(TimeValue(SCROLL_TIME_IN_HOURS, TimeUnit.HOURS)).execute().actionGet()
        } while (scrollResp.hits.hits.isNotEmpty())
    }

    fun save(blogArticles: List<BlogArticle>) {
        val bulkRequest = client.prepareBulk()
        for (blogArticle in blogArticles) {
            bulkRequest.add(client.prepareIndex(INDEX, TYPE, blogArticle.url)
                    .setSource(objectMapper.writeValueAsString(blogArticle), XContentType.JSON))
        }
        val result = bulkRequest.get()
        if (result.hasFailures()) {
            log.error("error saving blog articles: ${result.status()} ${result.buildFailureMessage()}")
        }
    }

    fun findAllFetchedUrls(url: String): List<String> {
        val response = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.matchPhraseQuery("blogUrl", url))
                .setFrom(0).setSize(20000)
                .get()

        if (response.hits == null) {
            return listOf()
        }

        return response.hits
                .map { objectMapper.readValue<BlogArticle>(it.sourceAsString, BlogArticle::class.java) }
                .map { it.url }
    }

    fun countByQuery(query: String) = client.prepareSearch(INDEX)
            .setTypes(TYPE)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .setQuery(QueryBuilders.queryStringQuery(query))
            .setFrom(0).setSize(0)
            .get()
            .hits
            .totalHits

    /**
     * counts all blog articles by given list of lucene syntax querys
     * @param querys list of lucene query strings
     * @return map with querystring and hit count
     */
    fun countByQuery(querys: List<String>): Map<String, Long> {
        val request = client.prepareMultiSearch()
        querys.map { query ->
            client.prepareSearch(INDEX)
                    .setTypes(TYPE)
                    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                    .setQuery(QueryBuilders.queryStringQuery(query))
                    .setFrom(0).setSize(0)
        }.forEach {
            request.add(it)
        }

        val result = mutableMapOf<String, Long>()
        var i = 0
        for (item in request.get()) {
            result.put(querys[i++], item.response.hits.totalHits)
        }
        return result
    }

    fun countByBlogUrl(url: String) = client.prepareSearch(INDEX)
                .setTypes(TYPE)
                .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                .setQuery(QueryBuilders.matchPhraseQuery("blogUrl", url))
                .setFrom(0).setSize(0)
                .get()
                .hits
                .totalHits

    fun countByBlogUrl(urls: List<String>): Map<String, Long> {
        val request = client.prepareMultiSearch()
        urls.map { url ->
            client.prepareSearch(INDEX)
                    .setTypes(TYPE)
                    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                    .setQuery(QueryBuilders.matchPhraseQuery("blogUrl", url))
                    .setFrom(0).setSize(0)
        }.forEach {
            request.add(it)
        }

        val result = mutableMapOf<String, Long>()
        var i = 0
        for (item in request.get()) {
            result.put(urls[i++], item.response.hits.totalHits)
        }
        return result
    }

    /**
     * find blog articles by given book. searches by isbn and author and title.
     * @param books list of books
     * @return list of matches with detailed information about every match
     */
    fun findByBooks(books: List<Book>): List<BookMatch> {
        val searchRequests = createSearchRequests(books)
        if (searchRequests.isEmpty()) {
            return listOf()
        }

        val request = client.prepareMultiSearch()
        searchRequests.map {
                    client.prepareSearch(INDEX)
                            .setTypes(TYPE)
                            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
                            .setQuery(QueryBuilders.queryStringQuery(it.query))
                            .setFrom(0).setSize(20000)
                }
                .forEach { request.add(it) }

        var i = 0
        val result = mutableListOf<BookMatch>()
        for (item in request.get()) {
            if (item.response.hits != null) {
                val query = searchRequests[i]
                val matches = item.response.hits
                        .map { objectMapper.readValue<BlogArticle>(it.sourceAsString, BlogArticle::class.java) }
                        .map { blogArticle ->
                            BookMatch(url = blogArticle.url, blogUrl = blogArticle.blogUrl, book = query.book.dnbId!!, byIsbn = query.byIsbn)
                        }
                result.addAll(matches)
            }
            i++
        }

        return result
    }

    /**
     * load blog articles by given urls
     * @param urls of the blog articles
     * @return map with url as key and blog article as value
     */
    fun findAllAssociateByUrl(urls: List<String>): Map<String, BlogArticle> {
        val request = client.prepareMultiGet()
        for (url in urls) {
            request.add(INDEX, TYPE, url)
        }

        val response = request.get()
        val result = mutableMapOf<String, BlogArticle>()
        for (itemResponse in response) {
            if (itemResponse.response.isExists) {
                val blogArticle = objectMapper.readValue<BlogArticle>(itemResponse.response.sourceAsString, BlogArticle::class.java)
                result.put(blogArticle.url, blogArticle)
            }
        }
        return result
    }

    fun count() = client.prepareSearch(INDEX)
                        .setTypes(TYPE)
                        .setSize(0)
                        .setQuery(matchAllQuery()).get().hits.getTotalHits()


    /**
     * create search requests for searching books in elastic search with lucene query string
     * for the book search. query string searches for isbn of the books and as second query for
     * title and authors of the books.
     * @param books for search query
     * @return list of search querys
     */
    private fun createSearchRequests(books: List<Book>): List<SearchRequest> {
        val searchRequests = mutableListOf<SearchRequest>()
        for (book in books) {
            val isbnQuery = createIsbnQuery(book)
            if (isbnQuery != null) {
                searchRequests.add(SearchRequest(query = isbnQuery, book = book, byIsbn = true))
            }

            val authorsTitleQuery = createTextQuery(book)
            if (authorsTitleQuery != null) {
                searchRequests.add(SearchRequest(query = authorsTitleQuery, book = book))
            }
        }
        return searchRequests
    }

    /**
     * creates lucene syntax query for isbn numbers of the book
     * @param book the searched book
     * @return lucene syntax query for isbn 10 and isbn 13 numbers
     */
    private fun createIsbnQuery(book: Book): String? {
        val queryIsbn13 = createIsbnQuery(book.isbn13)
        val queryIsbn10 = createIsbnQuery(book.isbn10)
        return when {
            queryIsbn10 != null && queryIsbn13 != null -> "$queryIsbn10 OR $queryIsbn13"
            queryIsbn10 == null && queryIsbn13 != null -> queryIsbn13
            queryIsbn10 != null && queryIsbn13 == null -> queryIsbn10
            else -> null
        }
    }

    /**
     * creates lucene syntax query for a single isbn
     * @param isbn isbn 10 or isbn 13 number
     * @return lucene search query string
     */
    private fun createIsbnQuery(isbn: String?): String? {
        if (isbn == null || isbn.trim().isEmpty()) {
            return null
        }

        val cleaned = isbn.replace("[^\\d.]", "")
        val formatted = try { ISBNHyphenAppender().appendHyphenToISBN(cleaned) } catch (e: Exception) { return null }
        val formattedSpaces = formatted.replace("-", " ")
        return try {
            "content:\"$formatted\" OR content:\"$cleaned\" OR content:\"$formattedSpaces\""
        } catch (e: Exception) {
            null
        }
    }

    /**
     * creates lucene syntax query for title and author search
     * @param book searched book
     * @return lucene search query string
     */
    private fun createTextQuery(book: Book): String? {
        val authors = book.authors
                ?.map { it.copy(forename = it.forename?.onlyAlphaNumeric(), surname = it.surname?.onlyAlphaNumeric()) }
                ?.filter { it.forename?.isNotEmpty() ?: false && it.surname?.isNotEmpty() ?: false }
                ?: listOf()

        val title = book.title?.trim()?.replace("\"", "") ?: ""

        if (authors.isEmpty() || title.isEmpty()) {
            return null
        }

        val authorContentQuery = createAuthorQuery(authors, "content")
        val authorTitleQuery = createAuthorQuery(authors, "title")

        if (authorContentQuery == "" || authorTitleQuery == "" || authorContentQuery.contains(title)) { // ignore when title is in author name (not specific enough)
            return null
        }

        return "(content:\"$title\" AND ($authorContentQuery)) OR (title:\"$title\" AND ($authorTitleQuery))"
    }

    /**
     * creates lucene syntax query for author search
     * @param authors the books authors
     * @param field the field for searching in
     * @return lucene search query string
     */
    private fun createAuthorQuery(authors: List<Author>, field: String): String {
        val onlyTypeAuthors = authors.filter { it.type == AuthorType.AUTHOR }
        val searchAuthors = if (onlyTypeAuthors.isNotEmpty()) onlyTypeAuthors else authors
        return searchAuthors
                .filter { it.forename?.trim()?.isNotEmpty() ?: false && it.surname?.trim()?.isNotEmpty() ?: false  }
                .joinToString(" OR") { "$field:\"${it.forename} ${it.surname}\"" }
    }

    private fun String.onlyAlphaNumeric() = this.replace(Regex("[^A-Za-z0-9 ]"), "").trim()

    data class SearchRequest(val book: Book, val query: String, val byIsbn: Boolean = false)
}