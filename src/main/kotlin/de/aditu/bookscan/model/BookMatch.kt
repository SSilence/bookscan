package de.aditu.bookscan.model

data class BookMatch(val url: String,
                     val book: String,
                     val blogUrl: String,
                     val byIsbn: Boolean = false,
                     var finished: Boolean = false)