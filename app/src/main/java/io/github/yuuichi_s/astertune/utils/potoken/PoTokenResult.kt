package io.github.yuuichi_s.astertune.utils.potoken

class PoTokenResult(
    /**
     * The visitorData the tokens were minted against. Requests carrying [streamingDataPoToken] have
     * to identify themselves with this exact value, otherwise the token is bound to one session and
     * presented by another, which googlevideo accepts for about a minute and then answers 403 to.
     */
    val visitorData: String,
    val playerRequestPoToken: String,
    val streamingDataPoToken: String,
)