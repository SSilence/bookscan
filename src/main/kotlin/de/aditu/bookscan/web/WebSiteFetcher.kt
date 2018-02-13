package de.aditu.bookscan.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.openqa.selenium.By
import org.openqa.selenium.chrome.ChromeDriver
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * fetches all pages of a given url
 */
@Service
class WebSiteFetcher(@Autowired private val webClient: WebClient,
                     @Autowired private val chromeDriver: ChromeDriver) {

    companion object {
        private val RETRY_HTTP_CLIENT = 3L
        private val WEBDRIVER_WAIT_IN_MS = 2000L
        private val READ_TIMEOUT_IN_MS = 30000L
        private val THREADS = 6
    }

    fun start(url: String,
              limit: Int = 0,
              ignore: List<String> = listOf(),
              simulateBrowser: Boolean = false,
              success: (content: Document, url: String) -> Boolean,
              error: (exception: Throwable, url: String) -> Boolean) {

        val fetcher = Fetcher(
                baseUrl = url,
                limit = limit,
                ignore = ignore,
                simulateBrowser = simulateBrowser,
                threads = if(simulateBrowser == true) 1 else THREADS,
                success = success,
                error = error,
                webClient = webClient,
                chromeDriver = chromeDriver)
        fetcher.scan(url)
        fetcher.waitUntilFinished()
    }


    data class Fetcher(val webClient: WebClient,
                       val chromeDriver: ChromeDriver,
                       val threads: Int = THREADS,
                       val executor: ExecutorService = Executors.newFixedThreadPool(threads),
                       val baseUrl: String,
                       val limit: Int = 0,
                       val ignore: List<String> = listOf(),
                       val simulateBrowser: Boolean = false,
                       val success: (content: Document, url: String) -> Boolean,
                       val error: (exception: Throwable, url: String) -> Boolean) {

        private val running = AtomicInteger(1)
        private val fetched = AtomicInteger(0)
        private val processed: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()

        fun scan(link: String) {
            try {
                val content = try {
                    if (simulateBrowser) {
                        loadInBrowser(link)
                    } else {
                        load(link)
                    }
                } catch (e: Exception) {
                    if (!error(e, link)) {
                        executor.shutdownNow()
                    }
                    null
                }

                var links = listOf<Element>()
                if (content != null) {
                    val doc = Jsoup.parse(content)
                    if (!success(doc, link)) {
                        executor.shutdownNow()
                    }
                    links = doc.select("a")
                }

                for (a in links) {
                    val linkUrl = parseUrl(baseUrl, a)
                    if (!isIgnoredUrl(linkUrl, ignore) && isInternalLink(linkUrl, baseUrl) && !processed.containsKey(linkUrl)) {
                        //log.info("${fetched.size} ${linkUrl}")
                        processed.put(linkUrl, false)
                        running.incrementAndGet()
                        executor.submit { scan(linkUrl) }
                    }
                }
            } finally {
                if ((limit != 0 && fetched.incrementAndGet() > limit) || running.decrementAndGet() == 0) {
                    executor.shutdownNow()
                }
            }
        }

        fun waitUntilFinished() {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS)
        }

        private fun isInternalLink(link: String, baseUrl: String) =
                removeBlogspotDomain(link).toLowerCase().startsWith(removeBlogspotDomain(baseUrl).toLowerCase())

        private fun removeBlogspotDomain(link: String) =
                link.replace(".blogspot.de", ".blogspot.")
                    .replace(".blogspot.com", ".blogspot.")
                    .replace(".blogspot.ch", ".blogspot.")
                    .replace(".blogspot.at", ".blogspot.")

        // handle ./ links and #hashes
        private fun parseUrl(baseUrl: String, a: Element?): String {
            if (a == null) {
                return ""
            }
            var url = a.absUrl("href")
            if (url.isEmpty()) {
                url = a.attr("href")
                url = if (url.startsWith("./")) url.substring(2) else url

                url = when {
                    url.startsWith("/") -> URI(baseUrl).scheme + "://" + URI(baseUrl).host + url
                    url == "." -> baseUrl
                    else -> baseUrl + "/" + url
                }
            }

            url = url.replace(".blogspot.co.at", ".blogspot.de")
            url = removeHash(url)
            return url
        }

        private fun isIgnoredUrl(linkUrl: String, ignore: List<String>): Boolean {
            val linkUri = try {
                URI(linkUrl)
            } catch (e: Exception) {
                return true
            }

            return linkUri.toString().trim().isEmpty()
                    || linkUri.path == null
                    || linkUri.path.startsWith("//")
                    || linkUri.path.startsWith("data:image")
                    || (linkUri.path == "/" && linkUri.query == null)
                    || linkUri.path.contains("admin.php")
                    || linkUri.path.contains("login.php")
                    || linkUri.path.contains("wp-admin")
                    || linkUri.path.contains("javascript:")
                    || linkUri.path.toLowerCase().endsWith("jpg")
                    || linkUri.path.toLowerCase().endsWith("png")
                    || linkUri.path.toLowerCase().endsWith("gif")
                    || linkUri.path.toLowerCase().endsWith("mp3")
                    || linkUri.path.toLowerCase().endsWith("avi")
                    || linkUri.path.toLowerCase().endsWith("mpg")
                    || linkUri.path.toLowerCase().endsWith("mpeg")
                    || (linkUri.query != null && linkUri.query.contains("m=1"))
                    || (linkUri.query != null && linkUri.query.contains("m=0"))
                    || ignore.filter { linkUrl.contains(it, true) }.count() > 0
        }

        private fun load(url: String): String? {
            return webClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .retry(RETRY_HTTP_CLIENT)
                    .block(Duration.ofMillis(READ_TIMEOUT_IN_MS))
        }

        private fun loadInBrowser(url: String): String? {
            chromeDriver.get(url)
            return try {
                Thread.sleep(WEBDRIVER_WAIT_IN_MS)
                chromeDriver.findElement(By.tagName("html")).getAttribute("innerHTML")
            } catch (exception: Exception) {
                ""
            }
        }

        private fun removeHash(url: String) = if (url.contains("#")) url.substring(0, url.indexOf("#")) else url
    }
}