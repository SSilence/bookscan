package de.aditu.bookscan.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.aditu.bookscan.model.UrlEntry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.*
import java.util.stream.Collectors

/**
 * starts the parallel blogprescan or blogscan with threads
 * uses different threadpools for blog with blogspot url, wordpress url, own domains and chromedriver processed urls
 */
@Service
class BlogScanStarter {

    companion object {
        private val THREADPOOL_SIZE_CHROMEDRIVER = 1
        private val THREADPOOL_SIZE_BLOGSPOT = 2
        private val THREADPOOL_SIZE_WORDPRESS = 2
        private val THREADPOOL_SIZE_OTHERS = 10
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun start(file: String, callback: (urlEntry: UrlEntry) -> Unit) {
        var urls = jacksonObjectMapper().readValue<List<UrlEntry>>(File(file), object : TypeReference<List<UrlEntry>>() {})
        log.info("${urls.size} URLs loaded")

        urls = urls.stream().sorted({u1, u2 -> u1.url.compareTo(u2.url)}).collect(Collectors.toList())

        val chromedriverTasks = urls.filter { it.simulateBrowser == true }.map { Runnable { callback(it) } }
        val blogspotTasks = urls.filter { it.getFetchUrl().contains("blogspot") && it.simulateBrowser != true }.map { Runnable { callback(it) } }
        val wordpressTasks = urls.filter { it.getFetchUrl().contains("wordpress") && it.simulateBrowser != true }.map { Runnable { callback(it) } }
        val othersTasks = urls.filter { !it.getFetchUrl().contains("wordpress") && !it.getFetchUrl().contains("blogspot") && it.simulateBrowser != true}
                .map { Runnable { callback(it) } }

        val chromedriverExecutor = start(chromedriverTasks, THREADPOOL_SIZE_CHROMEDRIVER)
        val blogspotExecutor = start(blogspotTasks, THREADPOOL_SIZE_BLOGSPOT)
        val wordpressExecutor = start(wordpressTasks, THREADPOOL_SIZE_WORDPRESS)
        val othersExecutor = start(othersTasks, THREADPOOL_SIZE_OTHERS)

        chromedriverExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
        blogspotExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
        wordpressExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)
        othersExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS)

        log.info("finished")
    }

    private fun start(tasks: List<Runnable>, threads: Int): ExecutorService {
        val executor = Executors.newFixedThreadPool(threads)
        tasks.forEach { executor.execute(it) }
        executor.shutdown()
        return executor
    }

}