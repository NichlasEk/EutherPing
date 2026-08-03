package se.apothictech.eutherping

internal data class MessageUrlMatch(
    val start: Int,
    val endExclusive: Int,
    val browserUrl: String,
)

private val MESSAGE_URL_PATTERN = Regex(
    pattern = "(?i)\\b(?:https?://|www\\.)[^\\s<>{}\\[\\]]+",
)

private val TRAILING_URL_PUNCTUATION = charArrayOf('.', ',', '!', '?', ';', ':', ')', '}', '\'', '"')

internal fun findMessageUrls(text: String): List<MessageUrlMatch> =
    MESSAGE_URL_PATTERN.findAll(text).mapNotNull { match ->
        val visible = match.value.trimEnd(*TRAILING_URL_PUNCTUATION)
        if (visible.isBlank()) return@mapNotNull null
        MessageUrlMatch(
            start = match.range.first,
            endExclusive = match.range.first + visible.length,
            browserUrl = if (visible.startsWith("www.", ignoreCase = true)) {
                "https://$visible"
            } else {
                visible
            },
        )
    }.toList()
