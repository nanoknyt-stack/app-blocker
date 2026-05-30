package com.example.appblocker

object BlockedApps {

    data class Rule(
        val packageName: String,
        val displayName: String
    )

    val rules: List<Rule> = listOf(
        Rule("com.zhiliaoapp.musically", "TikTok"),
        Rule("com.ss.android.ugc.trill", "TikTok"),
        Rule("com.google.android.youtube", "YouTube")
    )

    fun ruleFor(pkg: String): Rule? = rules.firstOrNull { it.packageName == pkg }

    val watchedPackages: Set<String> = rules.map { it.packageName }.toSet()
}

