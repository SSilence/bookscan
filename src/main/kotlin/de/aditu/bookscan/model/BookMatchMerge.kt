package de.aditu.bookscan.model

/**
 * a bookmatch with all information about the matching book and blog article for creating statistics
 */
data class BookMatchMerge(val murl: String,
                          val mblogUrl: String,
                          val mbyIsbn: Boolean = false,
                          val mbook: Book,
                          val mbookInfo: BookInfoToSave?,
                          var mblogArticle: BlogArticle)