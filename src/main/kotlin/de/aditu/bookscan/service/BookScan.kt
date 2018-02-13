package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BlogArticleRepository
import de.aditu.bookscan.elasticsearch.BookMatchRepository
import de.aditu.bookscan.elasticsearch.BookRepository
import de.aditu.bookscan.elasticsearch.KeyValueRepository
import de.aditu.bookscan.model.BookMatch

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * search books in fetched blog articles
 */
@Service
class BookScan(@Autowired private val bookMatchRepository: BookMatchRepository,
               @Autowired private val bookRepository: BookRepository,
               @Autowired private val blogArticleRepository: BlogArticleRepository,
               @Autowired private val keyValueRepository: KeyValueRepository) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val PROCESSED_CHUNK_KEY = "book_scan_processed_chunk"
        private val CHUNK_SIZE = 25_000
    }

    fun scan() {
        log.info("start book search")
        searchBooksInArticles()
        log.info("book search finished")
        Thread.sleep(60000) // ensure that elastic search finished all save tasks
        cleanupBookMatches()
        log.info("finished")
    }

    private fun searchBooksInArticles() {
        var index = 0
        val start = Integer.parseInt(keyValueRepository.load(PROCESSED_CHUNK_KEY, "-1"))
        bookRepository.chunk(CHUNK_SIZE) { books ->
            if (index <= start) {
                log.info("$index book chunk loaded; ignore processed chunk")
                index++
                return@chunk
            }

            log.info("$index book chunk loaded; ${books.size} books")
            val found = blogArticleRepository.findByBooks(books)
            if (found.isNotEmpty()) {
                bookMatchRepository.save(found)
            }
            keyValueRepository.save(PROCESSED_CHUNK_KEY, index.toString())
            log.info("${index++} book chunk processed; ${index * CHUNK_SIZE} books searched")
        }
    }

    private fun cleanupBookMatches() {
        val cleaned = loadAndCleanupBookList()
        bookMatchRepository.deleteAllFinished()
        bookMatchRepository.save(cleaned)
        log.info("all cleaned matches saved")

        bookMatchRepository.deleteAllNonFinished()
        log.info("all non finished matches removed")
    }

    private fun loadAndCleanupBookList(): List<BookMatch> {
        log.info("cleanup book matches")

        val clustered = bookMatchRepository.findAll().groupBy({ it.url }, { it })
        log.info("all matches loaded")

        val cleaned = mutableListOf<BookMatch>()
        for (item in clustered) {
            cleaned.addAll(cleanupBookList(item.value))
        }
        cleaned.forEach { it.finished = true }

        log.info("cleanup book matches finished")
        return cleaned
    }

    private fun cleanupBookList(books: List<BookMatch>): List<BookMatch> {
        val byIsbn = books.filter { it.byIsbn }
        // prefer isbn matches over others
        val accepted = if (byIsbn.isNotEmpty()) { byIsbn } else { books }
        return accepted.distinctBy { it.book }
    }



}