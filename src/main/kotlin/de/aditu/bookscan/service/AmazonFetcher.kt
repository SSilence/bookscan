package de.aditu.bookscan.service

import de.aditu.bookscan.amazon.AmazonClient
import de.aditu.bookscan.elasticsearch.BookInfoRepository
import de.aditu.bookscan.elasticsearch.BookMatchRepository
import de.aditu.bookscan.elasticsearch.BookRepository
import de.aditu.bookscan.model.BookInfo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * fetches all information from amazon for all matched books
 */
@Service
class AmazonFetcher(@Autowired private val bookMatchRepository: BookMatchRepository,
                    @Autowired private val bookRepository: BookRepository,
                    @Autowired private val bookInfoRepository: BookInfoRepository,
                    @Autowired private val amazonClient: AmazonClient) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val MAX_QUEUE_SIZE = 50
    }

    fun start() {
        val dnbIds = bookMatchRepository.findAll()
            .map { it.book }
            .distinct()
        log.info("dnbIds loaded")

        val isbns = bookRepository.findByDnbId(dnbIds)
                .map { book -> if (book.isbn10?.isEmpty() ?: true) book.isbn13 else book.isbn10 }
                .filter { it != null && it.isNotEmpty() }
                .map { it as String }
                .distinct()
        log.info("isbn loaded")

        val exists = bookInfoRepository.exists(isbns)
        log.info("existing bookinfos loaded")

        var queue = mutableListOf<BookInfo>()

        for (isbn in isbns) {
            if (exists[isbn] == true) {
                log.info("isbn $isbn already fetched")
                continue
            }

            var bookInfo = amazonClient.findByIsbn(isbn)
            if (bookInfo == null) {
                log.warn("no bookinfo for $isbn found")
                bookInfo = BookInfo(isbn, null, null, listOf(), false)
            }

            queue.add(bookInfo)
            if (queue.size > MAX_QUEUE_SIZE) {
                bookInfoRepository.save(queue)
                queue = mutableListOf()
            }
            log.info("saved bookinfo for $isbn")
        }

        if (queue.size > 0) {
            bookInfoRepository.save(queue)
        }

        log.info("finished")
    }

}