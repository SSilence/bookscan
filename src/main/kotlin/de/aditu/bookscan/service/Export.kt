package de.aditu.bookscan.service

import de.aditu.bookscan.elasticsearch.BookInfoRepository
import ninja.sakib.pultusorm.core.PultusORM
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*

/**
 * export book information fetched from amazon in sqlite database
 */
@Service
class Export(@Autowired private val bookInfoRepository: BookInfoRepository) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun start(target: String) {
        when (target) {
            "isbn" -> {
                log.info("start export isbn")
                val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                val pultusORM = PultusORM("isbn_${sdf.format(Date())}.db", Paths.get(".").toAbsolutePath().normalize().toString())
                bookInfoRepository.chunk { bookInfosToSave ->
                    for (bookInfoToSave in bookInfosToSave) {
                        pultusORM.save(bookInfoToSave)
                    }
                }
                pultusORM.close()
                log.info("export isbn finished")
            }
        }
    }

}