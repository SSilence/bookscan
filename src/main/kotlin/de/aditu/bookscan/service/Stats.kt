package de.aditu.bookscan.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.sun.net.httpserver.HttpServer
import de.aditu.bookscan.calc.SimilarityMatrixCalc
import de.aditu.bookscan.calc.SimilarityMatrixRenderer
import de.aditu.bookscan.elasticsearch.BlogArticleRepository
import de.aditu.bookscan.elasticsearch.BlogRepository
import de.aditu.bookscan.elasticsearch.BookMatchMergeRepository
import de.aditu.bookscan.model.AuthorType
import de.aditu.bookscan.model.BookMatchMerge
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

/**
 * calculate statistics and saves it into json files for javascript frontend
 */
@Service
class Stats(@Autowired private val bookMatchMergeRepository: BookMatchMergeRepository,
            @Autowired private val blogArticleRepository: BlogArticleRepository,
            @Autowired private val blogRepository: BlogRepository,
            @Autowired private val similarityMatrixCalc: SimilarityMatrixCalc,
            @Autowired private val similarityMatrixRenderer: SimilarityMatrixRenderer,
            @Autowired private val objectMapper: ObjectMapper) {

    companion object {
        private val BASE_PATH = Paths.get("./stats").toAbsolutePath().normalize().toString()
        private val MATRIX_FILENAME = "matrix.png"
        private val METRICS_FILENAME = "metrics.json"
        private val PUBLISHERS_FILENAME = "publishers.json"
        private val PUBLISHERS_ONLY_ISBN_FILENAME = "publishers_only_isbn.json"
        private val PUBLISHERS_BY_NAME_FILENAME = "publishers_by_name.json"
        private val SERVER_PORT = 8888
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun stats() {
        val matches = bookMatchMergeRepository.findAll().distinctBy { it.mbook.dnbId + it.mblogUrl } // ensure every book only once per blogurl
        matches.forEach {
            it.mbook.publisher = it.mbook.publisher?.trim() ?: "" // remove whitespaces on publisher
        }
        log.info("${matches.size} matches loaded")

        // overall stats
        metrics(matches)

        // similarity matrix
        matrix(matches)

        // publisher lists
        publisher(matches, PUBLISHERS_FILENAME)
        publisher(matches.filter { it.mbyIsbn }, PUBLISHERS_ONLY_ISBN_FILENAME)
        publisherByName(matches)

        // serve local webserver
        log.info("stats finished")
        serve()
    }

    private fun matrix(matches: List<BookMatchMerge>) {
        val matrix = similarityMatrixCalc.calc(matches)
        similarityMatrixRenderer.render(matrix, BASE_PATH + "/" + MATRIX_FILENAME)
        log.info("avg similarity: " + matrix.m.flatMap { it }.average())
    }

    private fun metrics(matches: List<BookMatchMerge>) {
        val result = mutableMapOf<String, Any>()

        val matchesByBlog = matches.groupBy({ it.mblogUrl }, { it })
        val matchesOnlyIsbn = matches.filter { it.mbyIsbn }

        // books found by isbn count
        result.put("booksByIsbnCount", matchesOnlyIsbn.count())

        // blog article count
        result.put("articleCount", blogArticleRepository.count())

        // blog article count by weekday
        result.put("articleCountByWeekday", articleCountByDateTimeFormat(matches, "EEEE"))

        // blog article count by month
        result.put("articleCountByMonth", articleCountByDateTimeFormat(matches, "yyyy.MM"))

        // blot article with Gewinnspiel in content
        result.put("articlesWithGewinnspiel", blogArticleRepository.countByQuery("content:\"*Gewinnspiel*\""))

        // blog count
        result.put("blogCount", blogRepository.count())

        // blog count
        result.put("matchCount", matches.size)

        result.put("matchesWithBookinfoCount", matches.filter { it.mbookInfo?.available ?: false }.size)

        // books without duplicate titles
        result.put("booksDistinctTitleCount", matchesByBlog.entries.sumBy { entry -> entry.value.distinctBy { it.mbook.title }.size })

        // age of blogs
        val firstEntryDateByBlog = mutableMapOf<String, Date>()
        for (match in matchesByBlog) {
            val minMatch = match.value.filter { it.mblogArticle.date.time > 0 }.minBy { it.mblogArticle.date.time }
            firstEntryDateByBlog.put(match.key, minMatch?.mblogArticle?.date ?: Date())
        }

        // avg age of blogs
        val now = Date().time
        val avgAgeInDays = firstEntryDateByBlog.toList()
                .map { (now - it.second.time) / (24 * 60 * 60 * 1000)  }
                .average()
        result.put("avgAgeInDays", avgAgeInDays)

        // 20 oldest blogs
        val oldestBlogs = firstEntryDateByBlog.toList()
                .sortedBy { it.second.time }
                .take(20)
                .toMap()
        result.put("oldestBlogs", oldestBlogs)

        // age histogramm by year/month
        val sdf = SimpleDateFormat("yyyy.MM", Locale.GERMANY)
        val ageHistogramm = firstEntryDateByBlog
                .toList()
                .map { it.second }
                .groupBy({ sdf.format(it) }, { it })
                .toList()
                .map { Pair(it.first, it.second.size) }
                .sortedBy { it.first }
                .toMap()
        result.put("ageHistogramm", ageHistogramm)

        // 20 blogs with most articles
        val mostArticleBlogs = blogArticleRepository.countByBlogUrl(matchesByBlog.map { it.key })
                .toList()
                .sortedBy { -it.second }
                .take(20)
                .toMap()
        result.put("mostArticleBlogs", mostArticleBlogs)

        // article content lengths
        val lengthsByBlogArticles = mutableMapOf<String, MutableList<Long>>()
        val blogArticleLengths = mutableListOf<Long>()
        blogArticleRepository.chunk { chunk ->
            for (blogArticle in chunk) {
                val length = blogArticle.content.length.toLong()
                blogArticleLengths.add(length)

                val list = lengthsByBlogArticles[blogArticle.blogUrl] ?: mutableListOf()
                list.add(length)
                lengthsByBlogArticles.put(blogArticle.blogUrl, list)
            }
        }
        val articleLengthSum = blogArticleLengths.sum()
        result.put("articleLengthSum", articleLengthSum)
        result.put("articleLengthAvg", articleLengthSum.toDouble() / blogArticleLengths.size.toDouble())
        result.put("articleLengthHistogramm", blogArticleLengths.groupBy({ it }, { it }).toList().map { Pair(it.first, it.second.size.toLong()) }.toMap())

        // 20 blogs with longest entries
        val longestArticlesBlogs = lengthsByBlogArticles.toList()
                .map { Pair(it.first, it.second.sum() / it.second.size) }
                .sortedBy { -it.second }
                .take(20)
                .toMap()
        result.put("longestArticlesBlogs", longestArticlesBlogs)

        // 20 most mentioned books
        val booksByTitleAndAuthor = matches
                .groupBy({ match ->
                    val authors = match.mbook.authors
                                        ?.filter { it.type == AuthorType.AUTHOR }
                                        ?.map {
                                            if (it.id == "") {
                                                it.id = "${it.forename} ${it.surname}"
                                            }
                                            it
                                        }
                                        ?.filter { it.id != "" }
                                        ?.distinctBy { it.id }
                                        ?.map { it.id }
                                        ?.sortedBy { it }
                                        ?.joinToString(",") ?: ""
                    if (authors == "") {
                        "invalid"
                    } else {
                        "${match.mbook.title}|$authors"
                    }
                }, { it })
                .toList()
                .filter { it.first != "invalid" }
                .map {
                    val matches = it.second.distinctBy { match -> match.murl } // remove duplicate matches for one blogpost
                    for (match in matches) {
                        match.mbook.authors = match.mbook.authors // remove duplicate authors
                                ?.filter { it.type == AuthorType.AUTHOR }
                                ?.map {
                                    if (it.id == "") {
                                        it.id = "${it.forename} ${it.surname}"
                                    }
                                    it
                                }
                                ?.filter { it.id != "" }
                                ?.distinctBy { it.id }
                    }
                    Pair(it.first, matches)
                }
        val top20Books = booksByTitleAndAuthor
                    .toList()
                    .sortedBy { -it.second.size }
                    .filter { it.second.isNotEmpty() }
                    .map { Pair(it.second.size, it.second[0]) }
                    .map { Triple(it.first, it.second.mbook, it.second.mbookInfo) }
                    .take(21)
        result.put("top20Books", top20Books)

        // 20 most mentioned books, ignore multiple mentions
        val top20BooksDistinctPerBlog = booksByTitleAndAuthor
                .toList()
                .map { Pair(it.first, it.second.distinctBy { match -> match.mblogUrl }) }
                .sortedBy { -it.second.size }
                .filter { it.second.isNotEmpty() }
                .map { Pair(it.second.size, it.second[0]) }
                .map { Triple(it.first, it.second.mbook, it.second.mbookInfo) }
                .take(20)
        result.put("top20BooksDistinctPerBlog", top20BooksDistinctPerBlog)

        // 20 most mentioned authors
        val top20Authors = booksByTitleAndAuthor
                .toList()
                .flatMap { it.second }
                .filter { it.mbook.authors != null }
                .flatMap { it.mbook.authors!!.filter { it.type == AuthorType.AUTHOR }.distinctBy { author -> author.id } }
                .groupBy({ it.id }, { it })
                .toList()
                .sortedBy { -it.second.size }
                .map { Pair(it.second.size, it.second[0]) }
                .take(20)
        result.put("top20Authors", top20Authors)

        // 20 most mentioned authors, ignore multiple mentions
        val top20AuthorsDistinctPerBlog = booksByTitleAndAuthor
                .toList()
                .flatMap { it.second }
                .filter { it.mbook.authors != null }
                .flatMap { match ->
                    match.mbook.authors!!
                            .filter { it.type == AuthorType.AUTHOR }
                            .distinctBy { author -> author.id }
                            .map { author -> Pair(author, match.mblogUrl) }
                }
                .groupBy({ it.first.id }, { it })
                .toList()
                .map { Pair(it.first, it.second.distinct()) }
                .sortedBy { -it.second.size }
                .map { Pair(it.second.size, it.second[0].first) }
                .take(20)
        result.put("top20AuthorsDistinctPerBlog", top20AuthorsDistinctPerBlog)

        // top genres
        result.put("genres", matches
                .flatMap {
                    it.mbookInfo?.toBookInfo()?.categories ?: listOf()
                }
                .map {
                    it.reversed()
                }
                .map {
                    if (it.size < 3) {
                        ""
                    } else {
                        it.joinToString(" -> ") { cat -> cat.name }
                    }
                }
                .filter { it != "" && !it.contains("Kindle") }
                .groupBy({ it }, { it })
                .toList()
                .map { Pair(it.first, it.second.size) }
                .sortedBy { -it.second }
                .toMap())

        saveToJson(result, METRICS_FILENAME)
    }

    private fun publisher(matches: List<BookMatchMerge>, filename: String) {
        val sdf = SimpleDateFormat("yyyy.MM", Locale.GERMANY)
        val matchesByMonth = matches.filter { it.mblogArticle.date.time > 0 }.groupBy({ sdf.format(it.mblogArticle.date) }, { it })

        val publisher = mutableMapOf<String, MutableMap<String, Int>>()
        for (matchByMonth in matchesByMonth) {
            val month = matchByMonth.key
            val matchesThisMonth = matchByMonth.value

            val matchesByPublisher = matchesThisMonth
                    .filter { it.mbook.publisher?.isNotEmpty() ?: false }
                    .groupBy({ it.mbook.publisher }, { it })

            val countByPublisher = mutableMapOf<String, Int>()
            for (publisherEntry in matchesByPublisher) {
                countByPublisher.put(publisherEntry.key!!, publisherEntry.value.size)
            }

            for (countEntry in countByPublisher) {
                if (publisher.containsKey(countEntry.key) == false) {
                    publisher.put(countEntry.key, mutableMapOf())
                }
                val countMap = publisher[countEntry.key]!!
                countMap.put(month, countEntry.value)
            }
        }

        val publisherSorted = publisher.toList().sortedBy { (_, value) -> -value.values.sum() }.toMap()

        saveToJson(publisherSorted, filename)
    }

    private fun publisherByName(matches: List<BookMatchMerge>) {
        val queries = matches.map { it.mbook.publisher }
                .filter { it?.trim()?.length ?: 0 > 0 }
                .map { it?.trim() }
                .groupBy({it},{it})
                .toList()
                .map { Pair(it.first, it.second.size) }
                .filter { it.second > 50 }
                .map { it.first }
                .distinct()
                .map { "content:\"$it\"" }
        val result = blogArticleRepository.countByQuery(queries)
        val sorted = result.toList()
                .sortedBy { (_, value) -> -value }
                .map { Pair(it.first.replace("content:\"", "").replace("\"", ""), it.second) }
                .toMap()
        saveToJson(sorted, PUBLISHERS_BY_NAME_FILENAME)
    }

    private fun serve() {
        val server = HttpServer.create(InetSocketAddress(SERVER_PORT), 0)
        server.createContext("/", { ex ->
            val uri = ex.requestURI
            val name = File(uri.path).name
            val path = File(BASE_PATH, name)

            val header = ex.responseHeaders
            header.add("Content-Type", "text/html")

            val out = ex.responseBody

            if (path.exists()) {
                ex.sendResponseHeaders(200, path.length())
                out.write(Files.readAllBytes(path.toPath()))
            } else {
                log.error("File not found: " + path.absolutePath)
                ex.sendResponseHeaders(404, 0)
                out.write("404 File not found.".toByteArray())
            }
            out.close()
        })

        log.info("start server on localhost:$SERVER_PORT")
        server.start()
    }

    private fun articleCountByDateTimeFormat(matches: List<BookMatchMerge>, format: String): Map<String, Int> {
        val sdf = SimpleDateFormat(format, Locale.GERMANY)
        val matchesByMonth = matches.filter { it.mblogArticle.date.time > 0 }.groupBy({ sdf.format(it.mblogArticle.date) }, { it })
        val result = mutableMapOf<String, Int>()
        matchesByMonth.forEach { e ->  result.put(e.key, e.value.size)}
        return result
    }

    private fun saveToJson(obj: Any, filename: String) {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT)
        val json = objectMapper.writeValueAsString(obj)
        File(BASE_PATH + "/" + filename).writeText(json)
        log.info("$filename created")
    }
}