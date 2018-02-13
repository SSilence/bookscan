package de.aditu.bookscan

import io.netty.channel.ChannelOption
import org.elasticsearch.client.Client
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.common.transport.InetSocketTransportAddress
import org.elasticsearch.transport.client.PreBuiltTransportClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.InetAddress
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.reactive.function.client.ClientRequest
import javax.net.ssl.SSLException

@Configuration
open class Configuration {

    companion object {
        private val SOCKET_TIMEOUT_IN_MS = 5000
        private val CONNECT_TIMEOUT_IN_MS = 5000
    }

    @Bean
    open fun client(): Client {
        return PreBuiltTransportClient(Settings.EMPTY)
                .addTransportAddress(InetSocketTransportAddress(InetAddress.getLoopbackAddress(), 9300))
    }

    @Bean
    @Throws(SSLException::class)
    open fun createWebClient(): WebClient {
        val sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build()

        val httpConnector = ReactorClientHttpConnector { opt ->
            opt.sslContext(sslContext)
            opt.option(ChannelOption.SO_TIMEOUT, SOCKET_TIMEOUT_IN_MS)
            opt.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_IN_MS)
        }

        return WebClient.builder()
                .filter({ request, next ->
                    next.exchange(ClientRequest.from(request)
                            .header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)")
                            .build())
                })
                .clientConnector(httpConnector).build()
    }

    @Bean
    open fun chromeDriver(@Value("\${webdriver.chrome.driver}") webdriverPath: String): ChromeDriver {
        System.setProperty("webdriver.chrome.driver", webdriverPath)
        val options = ChromeOptions()
        options.addArguments("headless")
        options.addArguments("window-size=1200x600")
        return ChromeDriver(options)
    }
}