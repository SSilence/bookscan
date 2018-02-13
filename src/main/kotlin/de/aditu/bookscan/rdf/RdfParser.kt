package de.aditu.bookscan.rdf

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import javax.xml.parsers.SAXParserFactory

/**
 * parser for dnb xml taken from https://data.dnb.de/opendata/
 * for parsing DNBTitel.rdf and GND.rdf
 */
@Service
class RdfParser {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val factory = SAXParserFactory.newInstance()

    /**
     * parse given rdf file
     * @param elementCallback will be called on every top level rdf:Description element
     * @param finishedCallback will be called after finishing parsing file
     */
    fun parse(filename: String,
              elementCallback: (rdfElement: RdfElement) -> Unit,
              finishedCallback: () -> Unit = {}) {
        log.info("start import of file $filename")

        try {
            factory.newSAXParser().parse(File(filename), object : DefaultHandler() {
                private var parsing = false
                private var current: RdfElement = RdfElement(attributes = null)

                override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                    if (qName?.equals("rdf:Description") == true && parsing == false) {
                        current = RdfElement(name = qName, attributesObject = attributes)
                        parsing = true
                    } else {
                        val parent = current
                        current = RdfElement(name = qName, attributesObject = attributes)
                        current.parent = parent
                        parent.children.add(current)
                    }
                }

                override fun characters(ch: CharArray?, start: Int, length: Int) {
                    if (ch == null) {
                        return
                    }

                    for (i in start until start + length) {
                        current.value = current.value + ch[i]
                    }
                }

                override fun endElement(uri: String?, localName: String?, qName: String?) {
                    if (current.parent != null) {
                        current = current.parent as RdfElement
                    }

                    if (qName?.equals("rdf:Description") == true && parsing && current.parent == null) {
                        elementCallback(current)
                        parsing = false
                    }
                }
            })

            finishedCallback()
        } catch (e: Exception) {
            log.error("parsing error", e)
        }
    }

}