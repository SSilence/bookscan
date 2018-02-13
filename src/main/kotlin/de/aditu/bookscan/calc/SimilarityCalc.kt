package de.aditu.bookscan.calc

import de.aditu.bookscan.model.BookMatchMerge
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * calculates the similarity of two blogs by comparing books and authors
 */
@Service
class SimilarityCalc {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val SCORE_SAME_BOOK = 15
        private val SCORE_SAME_AUTHOR = 5
        private val MIN_BOOK_COUNT = 15
    }

    /**
     * calculates the score between all given blogs
     * a score between 0 (= no similarity) and 100 (= very similar)
     */
    fun calc(matchesByBlog: Map<String, List<BookMatchMerge>>): Map<Pair<String, String>, Long> {
        val result = mutableMapOf<Pair<String, String>, Long>()
        val blogs = matchesByBlog.keys
        log.info("${blogs.size} blogs fetched")

        val booksByBlog = groupByFunction(matchesByBlog, { matches -> bookIds(matches)})
        val authorsByBlog = groupByFunction(matchesByBlog, { matches -> authorIds(matches)})
        log.info("blog to books, authors, categories prepared")

        val booksByBlogAndId = toSearchMap(booksByBlog)
        val authorsByBlogAndId = toSearchMap(authorsByBlog)
        log.info("maps for quick search for books, authors, categories prepared")

        var count = 0
        for (one in blogs) {
            for (second in blogs) {
                val key = Pair(one, second)
                val key2 = Pair(second, one)
                if (result.containsKey(key) || result.containsKey(key2)) {
                    continue
                }

                if (one == second) {
                    result.put(key, 100)
                    continue
                }

                var similarity = 0L
                for (book in booksByBlog[one] as List<String>) {
                    if (booksByBlogAndId.containsKey(Pair(second, book))) {
                        similarity += SCORE_SAME_BOOK
                    }
                }

                for (author in authorsByBlog[one] as List<String>) {
                    if (authorsByBlogAndId.containsKey(Pair(second, author))) {
                        similarity += SCORE_SAME_AUTHOR
                    }
                }

                if (booksByBlog[one]?.size ?: 0 < MIN_BOOK_COUNT || booksByBlog[second]?.size ?: 0 < MIN_BOOK_COUNT) {
                    result.put(key, 0)
                    continue
                }

                val maxPossibleScore = maxPossibleScore(one, second, booksByBlog, authorsByBlog)
                val score = ((similarity.toDouble() / maxPossibleScore.toDouble()) * 100).toLong()
                result.put(key, score)
            }

            log.info("${count++} blog $one processed")
        }
        return result
    }

    private fun groupByFunction(matchesByBlog: Map<String, List<BookMatchMerge>>, by: (bookMatchMerges: List<BookMatchMerge>) -> List<String>): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        for (matchByBlog in matchesByBlog) {
            result.put(matchByBlog.key, by(matchByBlog.value))
        }
        return result
    }

    private fun toSearchMap(map: Map<String, List<String>>): Map<Pair<String, String>, Boolean> {
        val result = mutableMapOf<Pair<String, String>, Boolean>()
        for (entry in map) {
            for (item in entry.value) {
                result.put(Pair(entry.key, item), true)
            }
        }
        return result
    }

    private fun bookIds(matches: List<BookMatchMerge>): List<String> =
            matches.filter { it.mbook.dnbId != null }
                    .map { it.mbook.dnbId!! }
                    .distinct()

    private fun authorIds(matches: List<BookMatchMerge>): List<String> =
            matches.flatMap { it.mbook.authors ?: listOf() }
                    .filter { it.id != null }
                    .map { it.id as String }
                    .distinct()

    private fun maxPossibleScore(one: String, second: String, booksByBlog: Map<String, List<String>>, authorsByBlog: Map<String, List<String>>): Int =
            minOf(booksByBlog[one]?.size ?: 0, booksByBlog[second]?.size ?: 0) * SCORE_SAME_BOOK + minOf(authorsByBlog[one]?.size ?: 0, authorsByBlog[second]?.size ?: 0) * SCORE_SAME_AUTHOR

}