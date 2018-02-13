package de.aditu.bookscan.web

import de.aditu.bookscan.model.UrlEntry
import org.springframework.stereotype.Service
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Whitelist
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * parses a given html page for blog article, date and title
 */
@Service
class WebSiteParser {

    private val titleSelectors = listOf(".blogpost h2.title", "h1.entry-title", "h2.entry-title", "header h3.entry-title", ".post-title", ".entry-title",
            ".post-header h1", ".posts_title", ".page-title", ".post_title", ".post-header h1", ".posttitle", ".single-title",
            ".main-title h1", "header.entry-header h1", "h3.post-title.entry-title", ".post-headline", "article header h1", "article header h2",
            "h2.postitle", ".post h1.title", ".post .title", ".j-blog-headline", "article h1", ".storytitle", ".post-caption",
            ".title-block h1", ".crt-post-single-title", ".title_post_standard", ".post-heading h3")
    private val dateSelectors = listOf(".post-meta-time", ".post-date", ".entry-date", ".entry-meta", ".date-header", ".timestamp-link",
            ".post-header .date", ".undertitle", "#date", ".post-meta", ".postmeta", ".post_date", ".timestamp-link", ".post-header-line-1",
            ".meta-date", ".meta_date", "meta[property=article:published_time]", ".date", ".blog-date", "meta[itemprop=datePublished]", ".published",
            ".date-header", "#Date", ".datetime", ".entry-published")
    private val articleSelectors = listOf(".sentry", ".entry-content", ".post-entry", ".entry", ".single-entry-content", ".post-content", ".post", "#content_area", ".post-body")
    private val removeSelectors = listOf("#comment", ".comment", "#comments", ".comments", ".commentlist", "#commentlist", ".PopularPosts",
            ".related-wrap", ".related-content-wrap", ".jp-relatedposts", "#jp-relatedposts", ".entry-related", ".post--related", ".yarpp-related",
            ".qode_portfolio_related", ".widget_popular_post", ".j-comment", ".related-posts-wrapper", ".related-wrap", ".widget_recent_entries",
            ".widget_recent_comments", ".related-posts", ".popular-posts", ".recent-posts", ".entry-related", ".entry-featured", ".widget_recent_entries",
            ".jetpack-likes-widget-wrapper", ".single-sharing-btns", ".addthis_toolbox", ".sharedaddy", ".post-share-buttons", ".social_share_privacy",
            ".post-share-buttons")

    private val dateRegexPatterns = listOf(
            "(\\d{4}\\/\\d{2}\\/\\d{2})",
            "(\\d{1,2}\\s*[a-zA-ZäÄ]+\\s*\\d{4})",
            "(\\d{1,2}\\.\\s*[a-zA-ZäÄ]+\\s*\\d{4})",
            "\\s+([a-zA-ZäÄ]{3,} \\d{1,2}, \\d{4})",
            "([a-zA-ZäÄ]{3,} \\d{1,2} \\d{4})",
            "([a-zA-ZäÄ]{3,} \\d{1,2}, \\d{4})",
            "(\\d{1,2}\\.\\d{2}\\.\\d{4})",
            "(\\d{4}\\-\\d{2}\\-\\d{2})",
            "(\\d{2}\\-\\d{2}\\-\\d{4})",
            "(\\d{2}\\/\\d{2}\\/\\d{4})",
            "(\\d{2}.\\d{2}.\\d{2})",
            "(\\d{1,2}\\.[a-zA-ZäÄ]{3,}\\s+\\d{4})",
            "\\s+([a-zA-ZäÄ]{3,} \\d{1,2}.{2}, \\d{4})",
            "([a-zA-ZäÄ]{3,} \\d{1,2}, \\d{4})"
    )
            .map { Pattern.compile(it, Pattern.DOTALL) }
    private val dateFormatPatterns = listOf(
            "dd. MMMM yyyy",
            "dd MMMM yyyy",
            "MMMM dd, yyyy",
            "MMM dd, yyyy",
            "MMM dd yyyy",
            "dd.MM.yyyy",
            "dd.MM.yy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "yyyy/MM/dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "dd.MMM yyyy",
            "MMMM d'st', yyyy",
            "MMMM d'nd', yyyy",
            "MMMM d'rd', yyyy",
            "MMMM d'th', yyyy")
            .flatMap { listOf(SimpleDateFormat(it, Locale.GERMANY), SimpleDateFormat(it, Locale.ENGLISH)) }

    private val isArticleUrlPattern = Pattern.compile("(\\d{4}/\\d{2}/).+", Pattern.DOTALL)
    private val isArticleUrlFullDatePattern = Pattern.compile("(\\d{4}/\\d{2}/\\d{2}/).+", Pattern.DOTALL)
    private val isArticleUrlFullDateWithoutTextPattern = Pattern.compile("\\d{4}/\\d{2}/\\d{2}/?$", Pattern.DOTALL)
    private val isArticleSelector = "body.single"

    fun parse(url: String, content: Document, urlEntry: UrlEntry): ParseResult {
        var dom = content
        if (urlEntry.removeRegex != null) {
            var html = dom.html()
            urlEntry.removeRegex.forEach {
                html = html.replace(Regex(it, setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)), "")
            }
            dom = Jsoup.parse(html)
        }

        val remove = mutableListOf<String>()
        remove.addAll(this.removeSelectors)
        if (urlEntry.removeSelector != null) {
            remove.addAll(urlEntry.removeSelector)
        }

        remove.flatMap { dom.select(it) }
                .filter { it != null }
                .forEach {
                    if (it.parentNode() != null) {
                        it.remove()
                    }
                }

        val isArticlePage = isArticlePage(url, dom, urlEntry.isArticlePageSelector, urlEntry.isArticlePagePattern)
        val isOverviewPage = isOverviewPage(dom, urlEntry.titleSelector)

        var date: Date? = null
        var title: String? = null
        var article: String? = null

        if (isArticlePage && !isOverviewPage) {
            date = fetchDate(url, dom, dateSelectors, urlEntry.dateSelector, urlEntry.useFallbackDateFromUrl ?: false)
            title = fetch(dom, titleSelectors, urlEntry.titleSelector)
            article = fetch(dom, articleSelectors, urlEntry.articleSelector)
        }

        return ParseResult(
                date = date,
                title = title,
                article = article,
                isOverviewPage = isOverviewPage,
                isArticlePage = isArticlePage
        )
    }

    private fun isArticlePage(url: String, content: Document, isArticlePageSelector: String? = null, isArticlePagePattern: String? = null): Boolean {
        if (isArticleUrlFullDateWithoutTextPattern.matcher(url).find()) {
            return false
        }

        return when {
            isArticlePageSelector != null -> content.select(isArticlePageSelector).size > 0
            isArticlePagePattern != null -> Pattern.compile(isArticlePagePattern, Pattern.DOTALL).matcher(url).find()
            else -> isArticleUrlPattern.matcher(url).find() || content.select(isArticleSelector).size > 0
        }
    }

    private fun isOverviewPage(content: Document, titleSelector: String?): Boolean {
        val selectors = if (titleSelector != null) listOf(titleSelector) else titleSelectors
        for (selector in selectors) {
            val elements = content.select(selector)
                    .filter { it.html() != null && it.html().trim().isNotEmpty() }
                    .count()
            if (elements == 1) {
                return false
            }
            if (elements > 1) {
                return true
            }
        }
        return false
    }

    private fun fetchDate(url: String, content: Document, querys: List<String>, selector: String?, useFallbackDateFromUrl: Boolean): Date? {
        if (useFallbackDateFromUrl) {
            return listOf(isArticleUrlFullDatePattern, isArticleUrlPattern)
                    .map { it.matcher(url) }
                    .filter { it.find() }
                    .map {
                        try { return@map SimpleDateFormat("yyyy/MM/dd").parse(it.group(1)) } catch (e: Exception) {}
                        try { return@map SimpleDateFormat("yyyy/MM/dd").parse(it.group(1) +  "01") } catch (e: Exception) {}
                        null
                    }
                    .filter { it != null }
                    .firstOrNull()
        }

        val querylist = if (selector != null) listOf(selector) else querys
        return querylist.flatMap { content.select(it) }
                .map {
                    when {
                        it.tagName().toLowerCase() == "time" -> it.attr("datetime")
                        it.tagName().toLowerCase() == "meta" -> it.attr("content")
                        it.tagName().toLowerCase() == "abbr" -> {
                            val title = it.attr("title")
                            if (title != null && title.isNotEmpty()) title else it.html()
                        }
                        else -> it.html()
                    }
                }
                .filter { it != null && it.trim().isNotEmpty() }
                .flatMap { text ->
                    dateRegexPatterns.map { pattern ->
                        val matcher = pattern.matcher(text)
                        if (!matcher.find()) null else matcher.group(1)
                    }
                }
                .filter { it != null }
                .flatMap { text ->
                    dateFormatPatterns.map { simpleDateFormat ->
                        try { simpleDateFormat.parse(text) } catch (e: Exception) { null }
                    }
                }
                .filter { it != null && it.time > Date().time - 630720000000L && it.time < Date().time}
                .firstOrNull()
    }

    private fun fetch(content: Document, querys: List<String>, selector: String?): String? {
        val querylist = if (selector != null) listOf(selector) else querys
        return querylist
                .flatMap { content.select(it) }
                .filter { it.html() != null && it.html().trim().isNotEmpty() }
                .map { it.html() }
                .filter { it != null && it.trim().isNotEmpty() }
                .map { Jsoup.clean(it, Whitelist.none()) }
                .map { it.replace("&nbsp;", " ") }
                .firstOrNull()
    }

    data class ParseResult(val date: Date? = null,
                      val title: String? = null,
                      val article: String? = null,
                      val isOverviewPage: Boolean,
                      val isArticlePage: Boolean)
}