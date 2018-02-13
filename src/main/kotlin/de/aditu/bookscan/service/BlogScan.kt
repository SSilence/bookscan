package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BlogArticleRepository
import de.aditu.bookscan.elasticsearch.BlogRepository
import de.aditu.bookscan.model.Blog
import de.aditu.bookscan.model.BlogArticle
import de.aditu.bookscan.model.UrlEntry
import de.aditu.bookscan.web.WebSiteFetcher
import de.aditu.bookscan.web.WebSiteParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

/**
 * fetches all blog articles and saves it into elasticsearch
 */
@Service
class BlogScan(@Autowired private val blogScanStarter: BlogScanStarter,
               @Autowired private val webSiteFetcher: WebSiteFetcher,
               @Autowired private val webSiteParser: WebSiteParser,
               @Autowired private val blogRepository: BlogRepository,
               @Autowired private val blogArticleRepository: BlogArticleRepository) {

    companion object {
        private val MAX_QUEUE_SIZE = 50
        private val ARTICLE_MIN_LENGTH = 600
    }

    private val log = LoggerFactory.getLogger(this.javaClass)
    private var counter = 0

    fun scan(file: String) = blogScanStarter.start(file, { scan(it) })

    private fun scan(urlEntry: UrlEntry) {
        if (blogRepository.findByUrl(urlEntry.url) != null) {
            log.info("${urlEntry.url} already fetched ${counter++}")
            return
        }
        log.info("${urlEntry.url} started")

        var queue = mutableListOf<BlogArticle>()

        val ignore = mutableListOf("/search", "/feed", "/feeds")
        if (urlEntry.ignoreUrl != null) {
            ignore.addAll(urlEntry.ignoreUrl)
        }
        ignore.addAll(blogArticleRepository.findAllFetchedUrls(urlEntry.url))

        webSiteFetcher.start(
                url = urlEntry.getFetchUrl(),
                ignore = ignore,
                simulateBrowser = urlEntry.simulateBrowser == true,
                success = { content, url ->
                    val parseResult = webSiteParser.parse(url, content, urlEntry)

                    if (!parseResult.isArticlePage || parseResult.isOverviewPage || parseResult.title == null || parseResult.article == null || parseResult.article.length < ARTICLE_MIN_LENGTH) {
                        return@start true
                    }

                    val date = parseResult.date ?: Date(0)

                    queue.add(BlogArticle(
                            url = url,
                            blogUrl = urlEntry.url,
                            date = date,
                            title = parseResult.title,
                            content = parseResult.article
                    ))

                    if (queue.size > MAX_QUEUE_SIZE) {
                        blogArticleRepository.save(queue)
                        queue = mutableListOf()
                    }

                    true
                },
                error = { _, _ ->
                    true
                })

        if (queue.size > 0) {
            blogArticleRepository.save(queue)
        }

        if (blogArticleRepository.countByBlogUrl(urlEntry.url) > 0) {
            blogRepository.save(Blog(urlEntry.url, Date()))
        }
        log.info("${urlEntry.url} finished ${counter++}")
    }

}