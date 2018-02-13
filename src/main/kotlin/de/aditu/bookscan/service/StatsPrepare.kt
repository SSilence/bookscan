package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.*
import de.aditu.bookscan.model.BookMatchMerge
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * prepare statistic calculation by creating merged book information in elasticsearch
 */
@Service
class StatsPrepare(@Autowired private val bookMatchRepository: BookMatchRepository,
                   @Autowired private val bookMatchMergeRepository: BookMatchMergeRepository,
                   @Autowired private val blogArticleRepository: BlogArticleRepository,
                   @Autowired private val bookRepository: BookRepository,
                   @Autowired private val bookInfoRepository: BookInfoRepository) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun start() {
        val existing = bookMatchMergeRepository.findAllUrls()
        log.info("all existing bookMatchMerge urls loaded")

        val bookInfos = bookInfoRepository.findAllAssociatedByIsbn()
        log.info("all bookInfos loaded")

        var index = 0
        bookMatchRepository.chunk { bookMatches ->
            val toSearch = bookMatches.filter { !existing.containsKey(it.url) }
            if (toSearch.isEmpty()) {
                log.info("chunk ${index++} no unprocessed matches found")
                return@chunk
            }

            val books = bookRepository.findAllAssociatedByDnbId(bookMatches.map { it.book })
            val blogArticles = blogArticleRepository.findAllAssociateByUrl(bookMatches.map { it.url })
            for (blogArticle in blogArticles) {
                blogArticle.value.content = ""
            }

            val toSave = mutableListOf<BookMatchMerge>()
            for (bookMatch in bookMatches) {
                val book = books[bookMatch.book]
                if (book == null) {
                    log.warn("book ${bookMatch.book} not found")
                    continue
                }

                val bookInfo = if (book.isbn10?.isNotEmpty() ?: true && bookInfos.containsKey(book.isbn10)) {
                    bookInfos[book.isbn10]
                } else if (book.isbn13?.isNotEmpty() ?: true && bookInfos.containsKey(book.isbn13)) {
                    bookInfos[book.isbn13]
                } else {
                    null
                }

                val blogArticle = blogArticles[bookMatch.url]
                if (blogArticle == null) {
                    log.warn("blog article ${bookMatch.url} not found")
                    continue
                }

                toSave.add(BookMatchMerge(
                    murl = bookMatch.url,
                    mblogUrl = bookMatch.blogUrl,
                    mbyIsbn = bookMatch.byIsbn,
                    mbook = book,
                    mbookInfo = bookInfo,
                    mblogArticle = blogArticle
                ))
            }

            bookMatchMergeRepository.save(toSave)

            log.info("chunk ${index++} processed")
        }
        log.info("stats prepare finished")
    }

}