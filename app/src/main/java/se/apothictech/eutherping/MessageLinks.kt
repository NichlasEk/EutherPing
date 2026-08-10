package se.apothictech.eutherping

internal data class MessageUrlMatch(
    val start: Int,
    val endExclusive: Int,
    val browserUrl: String,
)

private val MESSAGE_URL_PATTERN = Regex(
    pattern = "(?i)(?<![@\\w])(?:https?://)?" +
        "(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+" +
        "[a-z]{2,63}(?::\\d{1,5})?(?:[/?#][^\\s<>{}\\[\\]]*)?",
)

private val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', ')', '}', '\'', '"')

internal fun findMessageUrls(text: String): List<MessageUrlMatch> =
    MESSAGE_URL_PATTERN.findAll(text).mapNotNull { match ->
        val visible = match.value.trimEnd(*TRAILING_URL_PUNCTUATION)
        if (visible.isBlank()) return@mapNotNull null
        MessageUrlMatch(
            start = match.range.first,
            endExclusive = match.range.first + visible.length,
            browserUrl = if (visible.startsWith("http://", ignoreCase = true) ||
                visible.startsWith("https://", ignoreCase = true)
            ) {
                visible
            } else {
                "https://$visible"
            },
        )
    }.toList()
