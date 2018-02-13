package de.aditu.bookscan.rdf

import org.xml.sax.Attributes

/**
 * a RdfElement from dnb xml parsed by RdfParser
 * can hold recursively other RdfElements
 */
data class RdfElement(var name: String? = null,
                      var attributes: Map<String, String>? = null,
                      var value: String = "",
                      var children: MutableList<RdfElement> = mutableListOf(),
                      var parent: RdfElement? = null) {
    constructor(name: String? = null, attributesObject: Attributes? = null, value: String = "", children: MutableList<RdfElement> = mutableListOf(), parent: RdfElement? = null) :
            this(name = name, attributes = null, value = value, children = children, parent = parent) {
        attributes = attributesToMap(attributesObject)
    }

    override fun toString(): String {
        return "Element(name=$name)"
    }

    fun hasChildrenRecursive(qName: String, rdfElement: RdfElement = this): Boolean {
        if (rdfElement.children.find { el -> el.name == qName } != null) {
            return true
        }
        rdfElement.children.forEach {
            if (hasChildrenRecursive(qName, it) == true) {
                return true
            }
        }
        return false
    }

    fun findValue(qName: String, rdfElement: RdfElement = this): String {
        val searched = rdfElement.children.find { el -> el.name == qName }
        return searched?.value ?: ""
    }

    fun findValueRecursive(qName: String, rdfElement: RdfElement = this): String {
        val value = findValue(qName, rdfElement)
        if (value != "") {
            return value
        }
        rdfElement.children.forEach {
            val v = findValueRecursive(qName, it)
            if (v != "") {
                return v
            }
        }
        return ""
    }

    private fun attributesToMap(attributes: Attributes?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (attributes == null) {
            return map
        }
        for (i in 0 until attributes.length) {
            map.put(attributes.getQName(i), attributes.getValue(i))
        }
        return map
    }
}