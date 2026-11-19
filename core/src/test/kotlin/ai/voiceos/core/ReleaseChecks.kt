package ai.voiceos.core

internal fun verifyReleaseContracts() {
    val origin = checkNotNull(SiteOrigin.parse(" HTTPS://Voice.Example:443/ "))
    check(origin.home.toString() == "https://voice.example/")
    check(origin.releaseEndpoint.toString() == "https://voice.example/api/android/release")
    check(SiteOrigin.parse("https://voice.example:8443")?.home?.port == 8443)
    val unsafe = listOf("", "example.org", "http://example.org", "file:///tmp/voiceos", "https://user@example.org",
        "https://example.org/a", "https://example.org/?query=1", "https://example.org/#anchor", "https://example.org:0",
        "https://example.org:65536", "https://exa mple.org", "https://example.org\\@not-allowed.example")
    unsafe.forEach { input -> check(SiteOrigin.parse(input) == null) { "Expected origin rejection for $input" } }

    val signer = "ab".repeat(32)
    val sha = "cd".repeat(32)
    fun release(code: Int = 4, name: String = "0.4.0", minSdk: Int = 26, size: Int = 4096,
                id: String = "ai.voiceos", hash: String = sha, signing: String = signer,
                path: String = "/downloads/voice-os-$code.apk", schema: Int = 1): AndroidRelease? =
        AndroidRelease.parse(schema, id, code, name, minSdk, size, hash, signing, path)
    val current = checkNotNull(release())
    check(origin.download(current).toString() == "https://voice.example/downloads/voice-os-4.apk")
    check(current.decision(3, 36, setOf(signer)) == ReleaseDecision.DOWNLOAD)
    check(current.decision(4, 36, setOf(signer)) == ReleaseDecision.CURRENT)
    check(current.decision(5, 36, setOf(signer)) == ReleaseDecision.CURRENT)
    check(checkNotNull(release(minSdk = 35)).decision(3, 34, setOf(signer)) == ReleaseDecision.INCOMPATIBLE_ANDROID)
    check(current.decision(3, 36, setOf(sha)) == ReleaseDecision.DIFFERENT_SIGNATURE)
    check(current.decision(3, 36, emptySet()) == ReleaseDecision.DIFFERENT_SIGNATURE)
    check(current.decision(3, 36, setOf(signer, sha)) == ReleaseDecision.DIFFERENT_SIGNATURE)
    check(listOf(release(id = "ai.other"), release(schema = 9), release(code = 0), release(code = Int.MAX_VALUE),
        release(name = "<script>"), release(size = 1023), release(hash = "not-a-hash"), release(signing = "z".repeat(64)),
        release(path = "//other.example/voice-os-4.apk"), release(path = "https://other.example/voice-os-4.apk"),
        release(path = "/downloads/../voice-os-4.apk"), release(path = "/downloads/voice-os-5.apk")
    ).all { it == null })
    println("VOICE OS releases: HTTPS origin, metadata, Android compatibility, monotonic version and signing certificate contracts passed.")
}
