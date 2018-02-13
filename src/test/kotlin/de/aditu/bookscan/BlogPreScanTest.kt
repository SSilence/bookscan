package de.aditu.bookscan

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.aditu.bookscan.model.UrlEntry
import de.aditu.bookscan.service.BlogPreScan
import de.aditu.bookscan.service.BlogScanStarter
import de.aditu.bookscan.web.WebSiteFetcher
import de.aditu.bookscan.web.WebSiteParser
import org.junit.Ignore
import org.junit.Test

class BlogPreScanTest {

    @Test
    @Ignore
    fun testFile() {
        val configuration = Configuration()
        val blogPreScan = BlogPreScan(BlogScanStarter(), WebSiteFetcher(configuration.createWebClient(), configuration.chromeDriver("C:\\path\\to\\chromedriver.exe")), WebSiteParser())
        blogPreScan.scan("C:\\path\\to\\urls.json")
    }

    @Test
    fun testUrl() {
        val blogPreScan = BlogPreScan(
                BlogScanStarter(),
                WebSiteFetcher(Configuration().createWebClient(), Configuration().chromeDriver("C:\\path\\to\\chromedriver.exe")),
                WebSiteParser())

        blogPreScan.scan(jacksonObjectMapper().readValue("""
            {
                "url": ""
            }
            """, UrlEntry::class.java), detailedError = true)
    }
}
