package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BookRepository
import de.aditu.bookscan.model.Author
import de.aditu.bookscan.model.Book
import de.aditu.bookscan.rdf.RdfElement
import de.aditu.bookscan.rdf.RdfParser
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

data class DnbAuthor(var dnbId: String? = null, var surname: String? = null, var forename: String? = null)

/**
 * imports authors from GND.rdf file into elasticsearch downloaded from https://data.dnb.de/opendata/
 */
@Service
class DnbAutorImporter(@Autowired private val rdfParser: RdfParser,
                       @Autowired private val bookRepository: BookRepository) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    companion object {
        private val THREADPOOL_SIZE = 8
    }

    fun import(filename: String) {
        log.info("start author import from $filename")

        // import authors
        val authors = mutableMapOf<String, List<DnbAuthor>>()
        rdfParser.parse(filename, {
            val dnbAuthors = parseDnbAuthor(it)
            if (dnbAuthors.isNotEmpty()) {
                val dnbId = dnbAuthors[0].dnbId ?: return@parse
                authors.put(dnbId, dnbAuthors)
            }
        })
        log.info("${authors.size} authors imported")

        // assign authors to books
        log.info("assign authors")
        assignAuthors(authors)

        log.info("author import finished")
    }

    private fun parseDnbAuthor(rdfElement: RdfElement): List<DnbAuthor> {
        val authors = mutableListOf<DnbAuthor>()
        val dnbId = rdfElement.attributes?.get("rdf:about") ?: ""
        rdfElement.children
                .filter { it.name == "gndo:preferredNameEntityForThePerson" || it.name == "gndo:variantNameEntityForThePerson" }
                .forEach {
                    var forename = it.findValueRecursive("gndo:forename")
                    var surname = it.findValueRecursive("gndo:surname")

                    if (forename.trim().isEmpty() && surname.trim().isEmpty()) {
                        forename = it.findValueRecursive("gndo:personalName")
                        surname = it.findValueRecursive("gndo:nameAddition")
                    }

                    if (forename.trim().isNotEmpty() && surname.trim().isNotEmpty()) {
                        authors.add(DnbAuthor(dnbId = dnbId, forename = forename, surname = surname))
                    }
                }

        return authors
    }

    private fun assignAuthors(dnbAuthors: Map<String, List<DnbAuthor>>) {
        val threadPool = Executors.newFixedThreadPool(THREADPOOL_SIZE)

        var index = 0
        val count = AtomicInteger()
        bookRepository.chunk { books ->
            log.info("$index book chunk loaded")

            val toSave = Collections.synchronizedList(mutableListOf<Book>())
            val toDelete = Collections.synchronizedList(mutableListOf<Book>())

            val tasks = mutableListOf<Callable<Unit>>()
            for (book in books) {
                tasks.add(Callable {
                    when (assignAuthor(book, dnbAuthors)) {
                        Action.SAVE -> toSave.add(book)
                        Action.DELETE -> toDelete.add(book)
                        Action.NOTHING -> {}
                    }
                    if (count.incrementAndGet() % 100 == 0) {
                        log.info("$count assigned")
                    }
                })
            }

            try {
                threadPool.invokeAll(tasks)
            } catch (e: InterruptedException) {
                log.error("author import error", e)
            }

            if (toSave.isNotEmpty()) {
                bookRepository.save(toSave)
            }
            if (toDelete.isNotEmpty()) {
                bookRepository.delete(toDelete)
            }
            log.info("${index++} book chunk processed")
        }
    }

    private fun assignAuthor(book: Book, dnbAuthors: Map<String, List<DnbAuthor>>): Action {
        var action = Action.NOTHING
        val unknowns = book.authors?.filter { it.id?.length ?: 0 > 0 && it.forename?.length ?: 0 == 0 && it.surname?.length ?: 0 == 0 } ?: listOf()

        for (unknown in unknowns) {
            val authors = dnbAuthors[unknown.id]?.map { Author(id = it.dnbId, forename = it.forename, surname = it.surname, type = unknown.type) }
            if (authors == null || authors.isEmpty()) {
                log.warn("author ${unknown.id} not found")
                continue
            }

            val newAuthors = mutableListOf<Author>()
            newAuthors.addAll(book.authors?.filter { it.id != unknown.id } ?: listOf())
            newAuthors.addAll(authors)
            book.authors = newAuthors
            action = Action.SAVE
        }

        val validAuthors = book.authors?.filter { it.forename?.length ?: 0 > 0 && it.surname?.length ?: 0 > 0 }
        if (validAuthors?.isEmpty() ?: true) {
            action = Action.DELETE
        }

        return action
    }

    private enum class Action {
        SAVE,
        DELETE,
        NOTHING
    }
}