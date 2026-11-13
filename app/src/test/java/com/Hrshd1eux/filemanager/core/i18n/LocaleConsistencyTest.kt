package com.Hrshd1eux.filemanager.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

class LocaleConsistencyTest {

    private val supportedLocales = listOf(
        "values-es",
        "values-pt",
        "values-pt-rBR",
        "values-de",
        "values-ru",
        "values-fr",
        "values-hi",
        "values-bn",
        "values-mr",
        "values-te",
        "values-ta",
        "values-gu"
    )

    private val formatPattern = Pattern.compile("%(?:\\d+\\$)?[\\-+#, 0\\(<]*\\d*(?:\\.\\d+)?[a-zA-Z%]")

    private fun findResDir(): File {
        val candidates = listOf(
            File("app/src/main/res"),
            File("src/main/res"),
            File("../app/src/main/res")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Cannot locate app/src/main/res directory")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        val stringNodes = doc.getElementsByTagName("string")

        val result = mutableMapOf<String, String>()
        for (i in 0 until stringNodes.length) {
            val element = stringNodes.item(i) as Element
            val name = element.getAttribute("name")
            val content = element.textContent
            result[name] = content
        }
        return result
    }

    private fun extractPlaceholders(text: String): List<String> {
        val matcher = formatPattern.matcher(text)
        val list = mutableListOf<String>()
        while (matcher.find()) {
            val token = matcher.group()
            if (token != "%%") {
                list.add(token)
            }
        }
        return list.sorted()
    }

    @Test
    fun defaultStrings_hasValidKeys() {
        val resDir = findResDir()
        val defaultFile = File(resDir, "values/strings.xml")
        assertTrue("Default strings.xml must exist", defaultFile.exists())

        val defaultStrings = parseStringsXml(defaultFile)
        assertTrue("Default strings.xml should contain string entries", defaultStrings.isNotEmpty())
    }

    @Test
    fun allLocales_haveCompleteKeyParityAndPlaceholders() {
        val resDir = findResDir()
        val defaultFile = File(resDir, "values/strings.xml")
        val defaultStrings = parseStringsXml(defaultFile)
        val defaultKeys = defaultStrings.keys

        for (localeDirName in supportedLocales) {
            val localeFile = File(resDir, "$localeDirName/strings.xml")
            assertTrue("Locale file must exist: $localeDirName/strings.xml", localeFile.exists())

            val localeStrings = parseStringsXml(localeFile)
            val localeKeys = localeStrings.keys

            // 1. Key Parity Check
            val missingKeys = defaultKeys - localeKeys
            val extraKeys = localeKeys - defaultKeys

            assertTrue(
                "Missing keys in $localeDirName: $missingKeys",
                missingKeys.isEmpty()
            )
            assertTrue(
                "Extra dangling keys in $localeDirName: $extraKeys",
                extraKeys.isEmpty()
            )
            assertEquals(
                "Key count mismatch in $localeDirName",
                defaultKeys.size,
                localeKeys.size
            )

            // 2. Format Specifier / Placeholder Parity Check
            for ((key, defaultText) in defaultStrings) {
                val translatedText = localeStrings[key]
                assertTrue("Translated string for '$key' in $localeDirName should not be null", translatedText != null)

                val defaultPlaceholders = extractPlaceholders(defaultText)
                val translatedPlaceholders = extractPlaceholders(translatedText!!)

                assertEquals(
                    "Format placeholder mismatch for key '$key' in $localeDirName",
                    defaultPlaceholders,
                    translatedPlaceholders
                )
            }
        }
    }
}
