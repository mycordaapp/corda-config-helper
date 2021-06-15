package mycorda.app.cordaConfigHelper

import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList
import kotlin.text.StringBuilder


/**
 * ConfigFileEditor allows "inplace" editing of a node.conf
 * file (or any file using the HOCON format - https://github.com/typesafehub/config/blob/master/HOCON.md)
 *
 * This strategy has been choose as it allows design to keep the
 * node.conf as the definitive configuration whilst at the same time
 * allowing selective update to specific elements without affecting the
 * rest of the structure, for formatting, comments etc preserved as per the original
 *
 * TODO - A production quality implementation will probably need extra logic
 *        to support whitespaces and so on.
 */

/**
 * Base class for a token - this just represents plain text
 * over one or more lines
 */
open class Token(val raw: String) {
    override fun toString(): String {
        return "[ raw = '$raw' ]"
    }
}


class StartSectionToken(raw: String) : Token(raw) {
    fun sectionName(): String {
        val parts = raw.trim().split(Regex("\\s+"))
        return parts[0].replace("\"", "")
    }

    override fun toString(): String {
        return "[ sectionName = ${sectionName()}, raw =  '$raw' ]"
    }

}

class EndSectionToken(raw: String) : Token(raw)

class KeyValueToken(raw: String) : Token(raw) {

    fun key(): String {
        val parts = raw.split(Regex("="))
        return parts[0].trim()
    }

    fun value(): String {
        val parts = raw.split(Regex("="))
        return parts[1].trim()
    }

    fun updateValue(newValue: String): KeyValueToken {
        val parts = raw.split(Regex("="))
        return KeyValueToken(parts[0] + "=" + newValue)
    }

    override fun toString(): String {
        return "[ key = ${key()}, value = ${value()}, raw =  '$raw' ]"
    }
}


class Tokenizer(private val lines: Iterator<String>) : AbstractIterator<Token>() {

    object Regexp {
        val REGEXP_SECTION_START = Regex("\\s*\"?[\\w\\-_]+\"?[\\s=]+\\{")
        val REGEXP_SECTION_END = Regex("^\\s*}\\s*,?$")
        val REGEXP_KEY_VALUE = Regex("\\s*\\w+\\s*[=:].*")

    }

    // remaining tokens to return to iterator, before we need to start parsing again
    private val pendingTokens = LinkedList<Token>()

    override fun computeNext() {
        if (pendingTokens.isNotEmpty()) {
            setNext(pendingTokens.removeFirst())
            return
        }

        if (lines.hasNext()) {
            pendingTokens.addAll(processToken())
            // note, can only call setNext once with a single call to computeNext
            setNext(pendingTokens.removeFirst())
        } else {
            done()
        }
    }

    private fun processToken(): List<Token> {
        val result = ArrayList<Token>()
        while (lines.hasNext()) {
            val line = lines.next()

            return when {
                Regexp.REGEXP_SECTION_START.matches(line) -> {
                    val tk = StartSectionToken(line)
                    result.addAll(processSection(tk))
                    result
                }
                Regexp.REGEXP_KEY_VALUE.matches(line) -> listOf(KeyValueToken(line))
                else -> listOf(Token(line))
            }
        }

        throw RuntimeException("opps, this should never happen")
    }


    private fun processSection(start: StartSectionToken): List<Token> {
        val result = ArrayList<Token>()
        var endFound = false
        result.add(start)

        while (lines.hasNext() && !endFound) {
            val line = lines.next()

            // recurse into next section
            if (Regexp.REGEXP_SECTION_START.matches(line)) {
                val token = StartSectionToken(line)
                result.addAll(processSection(token))
            }

            // add any value
            else if (Regexp.REGEXP_KEY_VALUE.matches(line)) {
                result.add(KeyValueToken(line))
            }

            // drop out once we have found the end
            else if (Regexp.REGEXP_SECTION_END.matches(line)) {
                result.add(EndSectionToken(line))
                endFound = true
            } else {
                // anything else is just a Raw Token (whitespace, comments etc)
                result.add(Token(line))
            }
        }


        return result

    }

}


class ConfigFileEditor constructor(original: File) {


    private var tokens: List<Token> = ArrayList()

    init {
        val content = FileInputStream(original).bufferedReader().use { it.readText() }
        tokens = Tokenizer(content.lines().listIterator()).asSequence().toList()
    }

    fun updateKeyValueSection(sectionName: String, data: Map<String, String>, addIfMissing: Boolean = true) {
        updateKeyValueSection(listOf(sectionName), data, addIfMissing)
    }


    /**
     * Update the keys in a section
     */
    fun updateKeyValueSection(sectionNames: List<String>, data: Map<String, String>, addIfMissing: Boolean = true) {

        val stack = ArrayList(sectionNames)
        val rewritten = ArrayList<Token>(tokens.size)
        var sectionMatched = false
        var allSectionMatched = false
        var nesting = 0;
        val processed = HashSet<String>()


        var sectionName = stack[0]
        stack.removeAt(0)

        tokens.forEach { it ->
            var tk = it

            // Found start of section we care about
            if (!sectionMatched && tk is StartSectionToken && tk.sectionName() == sectionName) {

                if (stack.isEmpty()) {
                    allSectionMatched = true
                    sectionMatched = true

                } else {
                    sectionName = stack[0]
                    stack.removeAt(0)
                    sectionMatched = false
                }
            }

            // Internal nested section
            else if (allSectionMatched && tk is StartSectionToken) {
                nesting++
            }

            // we want to process this
            else if (allSectionMatched && nesting == 0 && tk is KeyValueToken && data.containsKey(tk.key())) {
                tk = tk.updateValue(data[tk.key()]!!)
                processed.add(tk.key())
            }

            // end of the section - any unmatched keys to add
            else if (tk is EndSectionToken && nesting == 0 && allSectionMatched) {
                allSectionMatched = false

                if (addIfMissing) {
                    for (item in data.entries) {
                        if (!processed.contains(item.key)) {
                            // todo - how to figure out indent from previous token to get
                            // a nicely formatted file
                            val tk1 = KeyValueToken("  ${item.key} = ${item.value}")
                            rewritten.add(tk1)
                            processed.add(tk1.key())
                        }
                    }
                }
            }

            // Internal nested section
            else if (allSectionMatched && nesting > 0 && tk is EndSectionToken) {
                nesting++
            }

            rewritten.add(tk)
        }


        // add the whole section
        if ((processed.size != data.size) && addIfMissing) {
            rewritten.add(StartSectionToken("$sectionName {"))

            for (item in data.entries) {
                if (!processed.contains(item.key)) {
                    // todo - how to figure out indent from previous token to get
                    // a nicely formatted file
                    val tk1 = KeyValueToken("  ${item.key} = ${item.value}")
                    rewritten.add(tk1)
                    processed.add(tk1.key())
                }
            }
            rewritten.add(EndSectionToken("}"))

        }



        tokens = rewritten
    }


    fun updateSectionKey(section: String, key: String, value: String, addIfMissing: Boolean = true) {
        updateKeyValueSection(section, mapOf(key to value), addIfMissing)
    }

    fun updateSectionKey(sections: List<String>, key: String, value: String, addIfMissing: Boolean = true) {
        updateKeyValueSection(sections, mapOf(key to value), addIfMissing)
    }

    /**
     * Update a key
     */
    fun updateKey(key: String, value: String, addIfMissing: Boolean = true) {

        val rewritten = ArrayList<Token>(tokens.size)

        // really we are just in the root section
        var nesting = 0
        var matched = false

        tokens.forEach { it ->
            var tk = it
            if (tk is StartSectionToken) {
                nesting++
            } else if (nesting == 0 && tk is KeyValueToken && tk.key() == key) {
                tk = tk.updateValue(value)
                matched = true
            } else if (tk is EndSectionToken) {
                nesting--
            }

            rewritten.add(tk)
        }
        if (!matched && addIfMissing) {
            rewritten.add(KeyValueToken("${key} = ${value}"))
        }
        tokens = rewritten
    }


    fun save(output: File) {
        var result = StringBuilder()
        tokens.joinTo(result, "\n") { it.raw }
        output.writeText(result.toString())
    }


    fun save(output: StringBuilder) {
        tokens.joinTo(output, "\n") { it.raw }
    }


}
