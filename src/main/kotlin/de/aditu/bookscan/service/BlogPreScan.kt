package de.aditu.bookscan.service

import de.aditu.bookscan.model.UrlEntry
import de.aditu.bookscan.web.WebSiteFetcher
import de.aditu.bookscan.web.WebSiteParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.*

/**
 * prescan for all blogs which checks whether blog articles can be fetched correctly
 */
@Service
class BlogPreScan(@Autowired private val blogScanStarter: BlogScanStarter,
                  @Autowired private val webSiteFetcher: WebSiteFetcher,
                  @Autowired private val webSiteParser: WebSiteParser) {

    companion object {
        private val MAX_PARSE_LIMIT = 350
        private val DEFAULT_MIN_PAGE_COUNT = 4
        private val ARTICLE_MIN_LENGTH = 700
    }

    private val log = LoggerFactory.getLogger(this.javaClass)
    private var counter = 0

    fun scan(file: String) = blogScanStarter.start(file, { scan(it) })

    fun scan(urlEntry: UrlEntry, detailedError: Boolean = false) {
        val results = mutableListOf<PreScanResult>()

        webSiteFetcher.start(
                url = urlEntry.getFetchUrl(),
                limit = MAX_PARSE_LIMIT,
                simulateBrowser = urlEntry.simulateBrowser == true,
                ignore = listOf("search/", "tag/", "tag=", "schlagwort/", "author/"),
                success = { content, url ->
                    val parseResult = webSiteParser.parse(url, content, urlEntry)

                    val resultTypes = mutableListOf<PreScanResultType>()
                    if (!parseResult.isArticlePage) {
                        resultTypes.add(PreScanResultType.NO_ARTICLE_PAGE)
                    }

                    if (parseResult.isOverviewPage) {
                        resultTypes.add(PreScanResultType.OVERVIEW_PAGE)
                    }


                    if (resultTypes.size == 0) {
                        if (parseResult.date == null && urlEntry.ignoreDate != true) {
                            resultTypes.add(PreScanResultType.NO_DATE_FOUND)
                        }
                        if (parseResult.date?.time ?: Date().time < Date().time - 630720000000L || parseResult.date?.time ?: Date().time > Date().time) {
                            resultTypes.add(PreScanResultType.INVALID_DATE_FOUND)
                        }
                        if (parseResult.title == null) {
                            resultTypes.add(PreScanResultType.NO_TITLE_FOUND)
                        }
                        if (parseResult.article == null) {
                            resultTypes.add(PreScanResultType.NO_ARTICLE_FOUND)
                        }
                        if (parseResult.article != null && parseResult.article.trim().length < ARTICLE_MIN_LENGTH) {
                            resultTypes.add(PreScanResultType.NO_ARTICLE_MIN_LENGTH)
                        }
                    }


                    if (resultTypes.size == 0) {
                        resultTypes.add(PreScanResultType.SUCCESS)
                    }

                    results.add(PreScanResult(url, resultTypes))

                    if (detailedError) {
                        log.info("$url $resultTypes")
                    }

                    !hasMininumSuccessPages(results, urlEntry.preScanMinPageCount ?: DEFAULT_MIN_PAGE_COUNT)
                },
                error = { exception, url ->
                    if (exception.cause != null && exception.cause is InterruptedException) {
                        return@start true
                    }
                    results.add(PreScanResult(url, listOf(PreScanResultType.FETCH_ERROR)))
                    if (detailedError) {
                        if ((exception is WebClientResponseException && exception.statusCode != HttpStatus.NOT_FOUND)
                                || exception is WebClientResponseException == false) {
                            log.error("$url fetch error", exception)
                        }
                    }
                    true
                })

        synchronized(this) {
            counter++
            if (hasMininumSuccessPages(results, urlEntry.preScanMinPageCount ?: DEFAULT_MIN_PAGE_COUNT)) {
                log.info("$counter SUCCESS ${urlEntry.url}")
            } else {
                log.info("$counter FAIL ${urlEntry.url}")
            }
        }
    }

    private fun hasMininumSuccessPages(results: MutableList<PreScanResult>, successPageCount: Int): Boolean =
        results.filter { it.result.contains(PreScanResultType.SUCCESS) }.count() >= successPageCount

    private data class PreScanResult(val url: String, val result: List<PreScanResultType>)

    private enum class PreScanResultType {
        SUCCESS,
        NO_DATE_FOUND,
        INVALID_DATE_FOUND,
        NO_TITLE_FOUND,
        NO_ARTICLE_FOUND,
        NO_ARTICLE_MIN_LENGTH,
        NO_ARTICLE_PAGE,
        OVERVIEW_PAGE,
        FETCH_ERROR
    }

}