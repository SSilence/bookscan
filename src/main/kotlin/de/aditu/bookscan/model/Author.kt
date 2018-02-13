package de.aditu.bookscan.model

enum class AuthorType {
    AUTHOR,
    CONTRIBUTOR
}

data class Author(var id: String? = null,
                  var surname: String? = null,
                  var forename: String? = null,
                  var type: AuthorType? = null)