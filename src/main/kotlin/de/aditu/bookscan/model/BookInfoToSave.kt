package de.aditu.bookscan.model

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ninja.sakib.pultusorm.annotations.PrimaryKey

/**
 * bookinfo with stringified categories for saving in elasticsearch or sqlite database
 */
data class BookInfoToSave(@PrimaryKey val isbn: String, val smallThumbnail: String?, val bigThumbnail: String?, val categories: String, val available: Boolean = true) {
    constructor(bookInfo: BookInfo) : this(
            isbn = bookInfo.isbn,
            smallThumbnail = bookInfo.smallThumbnail,
            bigThumbnail = bookInfo.bigThumbnail,
            categories = ObjectMapper().writeValueAsString(bookInfo.categories),
            available = bookInfo.available
    )

    fun toBookInfo(): BookInfo {
        return BookInfo(
                isbn = isbn,
                smallThumbnail = smallThumbnail,
                bigThumbnail = bigThumbnail,
                categories = ObjectMapper().readValue<List<List<BookCategory>>>(categories, object : TypeReference<List<List<BookCategory>>>() {}),
                available = available
        )
    }
}