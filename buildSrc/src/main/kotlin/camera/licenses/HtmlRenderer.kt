package camera.licenses

internal object HtmlRenderer {

    private val headBefore: String
    private val headAfter: String

    init {
        val template = readResource("html/head.html")
        val parts = template.split(TITLE_PLACEHOLDER)

        require(parts.size == 2) {
            "html/head.html must contain exactly one $TITLE_PLACEHOLDER"
        }

        headBefore = parts[0]
        headAfter = parts[1]
    }

    fun render(
        title: String,
        blocks: List<NoticeBlock>,
    ): String = buildString {
        append(headBefore)
        append(title.htmlEscape())
        append(headAfter)
        for (block in blocks) {
            appendBlock(
                heading = block.heading,
                body = block.body,
            )
        }
        append(TAIL)
    }

    private fun StringBuilder.appendBlock(
        heading: String,
        body: String,
    ) {
        append("<details>\n")
        append("<summary><span class=\"label\">")
        append(heading.htmlEscape())
        append("</span><span class=\"chev\" aria-hidden=\"true\">+</span></summary>\n")
        append("<pre>\n")
        append(body.htmlEscape())
        append("\n</pre>\n</details>\n\n")
    }

    private fun readResource(path: String): String {
        return javaClass.classLoader.getResource(path)
            ?.readText()
            ?: error("Missing resource: $path")
    }

    private fun String.htmlEscape(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private const val TITLE_PLACEHOLDER = "{title}"
    private const val TAIL = "</body></html>\n"
}
