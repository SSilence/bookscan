package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BookRepository
import de.aditu.bookscan.model.Author
import de.aditu.bookscan.model.AuthorType
import de.aditu.bookscan.model.Book
import de.aditu.bookscan.rdf.RdfElement
import de.aditu.bookscan.rdf.RdfParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * import books from DNBTitel.rdf into elasticsearch downloaded from https://data.dnb.de/opendata/
 */
@Service
class DnbBookImporter(@Autowired private val bookRepository: BookRepository,
                      @Autowired private val rdfParser: RdfParser) {

    companion object {
        private val QUEUE_BLOCK_SIZE = 100_000
    }

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val queue = mutableListOf<Book>()
    private var parsed = 0
    private var saved = 0

    fun import(filename: String) {
        log.info("start import")

        rdfParser.parse(filename,
                { save(it) },
                { writeQueue() })
    }

    private fun save(rdfElement: RdfElement) {
        val book = Book()
        book.dnbId = rdfElement.attributes?.get("rdf:about")
        book.title = rdfElement.findValue("dc:title")
        book.isbn10 = rdfElement.findValue("bibo:isbn10")
        book.isbn13 = rdfElement.findValue("bibo:isbn13")
        book.issued = rdfElement.findValue("dcterms:issued")
        book.publisher = rdfElement.findValue("dc:publisher")
        book.authors = findAuthors(rdfElement)

        parsed++

        // ignore books without isbn or author
        if (!book.hasISBN() || !book.hasAuthors()) {
            return
        }

        queue.add(book)

        if (queue.size > QUEUE_BLOCK_SIZE) {
            writeQueue()
        }
    }

    private fun findAuthors(rdfElement: RdfElement): List<Author>? {
        val authors = rdfElement.children
                .filter { it.name == "dcterms:creator" && (it.attributes?.get("rdf:resource") ?: "").isNotEmpty() }
                .map {
                    Author(id = it.attributes?.get("rdf:resource") ?: "", type = AuthorType.AUTHOR)
                }
                .toMutableList()

        val embeddedAuthorsContributors = rdfElement.children
                .filter { (it.name == "dcterms:creator" || (it.name ?: "").startsWith("marcRole")) && it.hasChildrenRecursive("gndo:preferredName") }
                .map {
                    val value = it.findValueRecursive("gndo:preferredName")

                    val author = Author(id = "")

                    val split = value.split(",")
                    if (split.size == 2) {
                        author.surname = split[0].trim()
                        author.forename = split[1].trim()
                    } else {
                        author.surname = value
                    }

                    if (it.name == "dcterms:creator") {
                        author.type = AuthorType.AUTHOR
                    } else {
                        author.type = AuthorType.CONTRIBUTOR
                    }

                    author
                }
                .toMutableList()

        val contributors = rdfElement.children
                .filter { (it.name ?: "").startsWith("marcRole") && (it.attributes?.get("rdf:resource") ?: "").isNotEmpty() }
                .map {
                    Author(id = it.attributes?.get("rdf:resource") ?: "", type = AuthorType.CONTRIBUTOR)
                }
                .toList()

        val result = mutableListOf<Author>()
        result.addAll(authors)
        result.addAll(embeddedAuthorsContributors)
        result.addAll(contributors)
        return result
    }

    private fun writeQueue() {
        bookRepository.save(queue)
        queue.clear()
        saved += QUEUE_BLOCK_SIZE
        log.info("saved $saved")
    }

}