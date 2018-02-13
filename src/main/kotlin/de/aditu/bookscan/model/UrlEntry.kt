package de.aditu.bookscan.model

/**
 * url entry from urls.json
 */
data class UrlEntry(val url: String,
                    val feed_ignore: Boolean? = null,
                    val feed: String? = null,

                    val frame: String? = null,
                    val articleSelector: String? = null,
                    val titleSelector: String? = null,
                    val dateSelector: String? = null,
                    val isArticlePageSelector: String? = null,
                    val isArticlePagePattern: String? = null,
                    val useFallbackDateFromUrl: Boolean? = null,
                    val preScanMinPageCount: Int? = null,
                    val ignoreDate: Boolean? = null,
                    val ignoreUrl: List<String>? = null,

                    val removeSelector: List<String>? = null,
                    val removeRegex: List<String>? = null,

                    val simulateBrowser: Boolean? = null) {
    fun getFetchUrl(): String = frame ?: url
}