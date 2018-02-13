package de.aditu.bookscan.model

data class BookInfo(val isbn: String, val smallThumbnail: String?, val bigThumbnail: String?, val categories: List<List<BookCategory>>, val available: Boolean = true)

data class BookCategory(val id: String, val name: String) {
    constructor() : this("","")
}