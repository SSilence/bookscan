package de.aditu.bookscan.amazon

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.*
import java.text.SimpleDateFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * signs amazon product advertising api requests
 * taken from https://docs.aws.amazon.com/AWSECommerceService/latest/DG/AuthJavaSampleSig2.html
 */
@Component
class AmazonUrlSigner(@Value("\${amazonAccesskey}") private val amazonAccesskey: String,
                      @Value("\${amazonSecret}") private val amazonSecret: String) {

    companion object {
        private val UTF8_CHARSET = "UTF-8"
        private val HMAC_SHA256_ALGORITHM = "HmacSHA256"
        private val REQUEST_URI = "/onca/xml"
        private val REQUEST_METHOD = "GET"
        private val ENDPOINT = "webservices.amazon.de"
    }

    private val secretKeySpec: SecretKeySpec
    private val mac: Mac

    init {
        val secretyKeyBytes = amazonSecret.toByteArray(Charset.forName(UTF8_CHARSET))
        secretKeySpec = SecretKeySpec(secretyKeyBytes, HMAC_SHA256_ALGORITHM)
        mac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        mac.init(secretKeySpec)
    }

    /**
     * creates url and sign given parameters for calling amazon product advertising api
     * @param params the get paramters
     * @return complete url for calling amazon
     */
    fun sign(params: MutableMap<String, String>): String {
        params.put("AWSAccessKeyId", amazonAccesskey)
        params.put("Timestamp", timestamp()!!)

        val sortedParamMap = TreeMap(params)
        val canonicalQS = canonicalize(sortedParamMap)
        val toSign = (REQUEST_METHOD + "\n" + ENDPOINT + "\n" + REQUEST_URI + "\n" + canonicalQS)

        val hmac = hmac(toSign)
        val sig = percentEncodeRfc3986(hmac)

        return "http://$ENDPOINT$REQUEST_URI?$canonicalQS&Signature=$sig"
    }

    private fun hmac(stringToSign: String): String {
        val signature: String?
        val data: ByteArray
        val rawHmac: ByteArray
        try {
            data = stringToSign.toByteArray(Charset.forName(UTF8_CHARSET))
            rawHmac = mac.doFinal(data)
            val encoder = Base64.getEncoder()
            signature = String(encoder.encode(rawHmac))
        } catch (e: UnsupportedEncodingException) {
            throw RuntimeException(UTF8_CHARSET + " is unsupported!", e)
        }

        return signature
    }

    private fun timestamp(): String? {
        val timestamp: String?
        val cal = Calendar.getInstance()
        val dfm = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        dfm.timeZone = TimeZone.getTimeZone("GMT")
        timestamp = dfm.format(cal.time)
        return timestamp
    }

    private fun canonicalize(sortedParamMap: SortedMap<String, String>): String {
        if (sortedParamMap.isEmpty()) {
            return ""
        }

        val buffer = StringBuffer()
        val iter = sortedParamMap.entries.iterator()

        while (iter.hasNext()) {
            val kvpair = iter.next()
            buffer.append(percentEncodeRfc3986(kvpair.key))
            buffer.append("=")
            buffer.append(percentEncodeRfc3986(kvpair.value))
            if (iter.hasNext()) {
                buffer.append("&")
            }
        }
        return buffer.toString()
    }

    private fun percentEncodeRfc3986(s: String) =
            try {
                URLEncoder.encode(s, UTF8_CHARSET)
                        .replace("+", "%20")
                        .replace("*", "%2A")
                        .replace("%7E", "~")
            } catch (e: UnsupportedEncodingException) {
                s
            }
}