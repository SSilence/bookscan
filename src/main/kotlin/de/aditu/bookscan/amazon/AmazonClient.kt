package de.aditu.bookscan.amazon

import com.amazon.webservices.awsecommerceservice._2011_08_01.BrowseNode
import com.amazon.webservices.awsecommerceservice._2011_08_01.ItemLookupResponse
import de.aditu.bookscan.model.BookCategory
import de.aditu.bookscan.model.BookInfo
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.io.StringReader
import java.net.URI
import java.time.Duration
import javax.xml.bind.JAXBContext

/**
 * client for accessing amazon product advertising api
 */
@Component
class AmazonClient(@Autowired private val webClient: WebClient,
                   @Autowired private val amazonUrlSigner: AmazonUrlSigner,
                   @Value("\${amazonAccesskey}") private val amazonAccesskey: String,
                   @Value("\${amazonTag}") private val amazonTag: String) {

    companion object {
        private val READ_TIMEOUT_IN_MS = 30000L
        private val MAX_RETRIES = 15
    }

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val jaxbUnmarshaller = JAXBContext.newInstance(ItemLookupResponse::class.java).createUnmarshaller()


    /**
     * find book description by isbn
     * @param isbn the ISBN10 or ISBN13 of the searched book
     */
    fun findByIsbn(isbn: String): BookInfo? {
        val params = mutableMapOf<String, String>()
        params.put("Service", "AWSECommerceService")
        params.put("AWSAccessKeyId", amazonAccesskey)
        params.put("AssociateTag", amazonTag)
        params.put("Operation", "ItemLookup")
        params.put("IdType", "ISBN")
        params.put("ItemId", isbn)
        params.put("SearchIndex", "Books")
        params.put("ResponseGroup", "Images,ItemAttributes,BrowseNodes")
        val url = amazonUrlSigner.sign(params)

        val retry = 0

        while(retry < MAX_RETRIES) {
            try {
                val result = webClient.get()
                        .uri(URI.create(url))
                        .retrieve()
                        .bodyToMono(String::class.java)
                        .block(Duration.ofMillis(READ_TIMEOUT_IN_MS))

                if (result?.contains("AWS.ECommerceService.ItemNotAccessible") == true || result?.contains("AWS.InvalidParameterValue") == true) {
                    log.warn("article not available $isbn")
                    return null
                }

                val response = jaxbUnmarshaller.unmarshal(StringReader(result)) as ItemLookupResponse
                if (response.items == null || response.items.isEmpty() || response.items[0].item == null || response.items[0].item.isEmpty()) {
                    throw RuntimeException("no items in result found")
                }

                return BookInfo(isbn = isbn,
                        smallThumbnail = if (response.items[0].item[0].mediumImage != null) response.items[0].item[0].mediumImage.url else null,
                        bigThumbnail = if (response.items[0].item[0].largeImage != null) response.items[0].item[0].largeImage.url else null,
                        categories = fetchCategories(response))
            } catch (e: Exception) {
                //log.error("error fetching amazon book ${isbn}: ${result}", e)
                Thread.sleep(1000)
            }
        }

        log.error("stop fetching $isbn after $MAX_RETRIES failing retries")
        return null
    }

    private fun fetchCategories(response: ItemLookupResponse): List<List<BookCategory>> {
        if (response.items[0].item[0].browseNodes == null
                || response.items[0].item[0].browseNodes.browseNode == null
                || response.items[0].item[0].browseNodes.browseNode.isEmpty()) {
            return mutableListOf()
        }

        return response.items[0].item[0].browseNodes.browseNode
                .flatMap { fetchCategories(it) }
    }

    private fun fetchCategories(node: BrowseNode): List<List<BookCategory>> {
        val current = BookCategory(node.browseNodeId ?: "", node.name ?: "")
        if (node.ancestors == null || node.ancestors.browseNode == null || node.ancestors.browseNode.isEmpty()) {
            return mutableListOf(mutableListOf(current))
        }

        return node.ancestors.browseNode
                .flatMap { fetchCategories(it) }
                .map { categories ->
                    val listWithCurrent = mutableListOf<BookCategory>()
                    categories.filter {
                        it.name != "Bücher" && it.name != "Kategorien" && it.name != "" && it.id != ""
                    }
                    .forEach {
                        listWithCurrent.add(it)
                    }
                    listWithCurrent.add(current)
                    listWithCurrent
                }
    }
}