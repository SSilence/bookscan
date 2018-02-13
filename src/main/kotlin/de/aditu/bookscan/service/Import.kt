package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BookInfoRepository
import de.aditu.bookscan.model.BookInfoToSave
import ninja.sakib.pultusorm.core.PultusORM
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.nio.file.Paths

/**
 * imports book information fetched from amazon from sqlite database
 */
@Service
class Import(@Autowired private val bookInfoRepository: BookInfoRepository) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun start(target: String, file: String) {
        when (target) {
            "isbn" -> {
                log.info("start import isbn $file")
                val pultusORM = PultusORM(file, Paths.get(".").toAbsolutePath().normalize().toString())
                val bookInfos = pultusORM.find(BookInfoToSave("", "", "", "")) as List<BookInfoToSave>
                if (bookInfos.isEmpty()) {
                    log.error("no bookinfos found")
                } else {
                    bookInfoRepository.saveBookInfoToSave(bookInfos)
                }
                pultusORM.close()
                log.info("import isbn finished")
            }
        }
    }

}