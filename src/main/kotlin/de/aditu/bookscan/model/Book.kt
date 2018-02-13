package de.aditu.bookscan.model

data class Book(var dnbId: String? = null,
                var title: String? = null,
                var authors: List<Author>? = null,
                var publisher: String? = null,
                var issued: String? = null,
                var isbn10: String? = null,
                var isbn13: String? = null) {

    fun hasISBN(): Boolean {
        val i10: String = isbn10 ?: return false
        val i13: String = isbn13 ?: return false

        return i10.trim().isNotEmpty() || i13.trim().isNotEmpty()
    }

    fun hasAuthors(): Boolean {
        val c = authors ?: return false
        return c.isNotEmpty()
    }

}