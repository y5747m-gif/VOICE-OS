package ai.voiceos.core

import java.text.Normalizer
import java.util.Locale

sealed interface Interpretation {
    data class Matched(val action: DeviceAction) : Interpretation
    data class NotMatched(val reason: Unmatched) : Interpretation
}

/** A bounded local grammar, not a model. Every accepted phrase must match in full. */
class LocalInterpreter {
    fun interpret(input: String): Interpretation {
        if (input.length > MAX_COMMAND_LENGTH) return Interpretation.NotMatched(Unmatched.TOO_LONG)
        var phrase = normalize(input)
        if (phrase.isEmpty()) return Interpretation.NotMatched(Unmatched.EMPTY)
        phrase = politenessPrefixes.firstOrNull { phrase.startsWith("$it ") || phrase.startsWith("$it, ") || phrase.startsWith("$it، ") }
            ?.let { phrase.removePrefix(it).trimStart(' ', ',', '،') } ?: phrase
        phrase = politenessSuffixes.firstOrNull { phrase.endsWith(" $it") }
            ?.let { phrase.removeSuffix(" $it").trimEnd().removeSuffix(",").removeSuffix("،").trimEnd() } ?: phrase

        return phrases[phrase]?.let(Interpretation::Matched) ?: Interpretation.NotMatched(
            if (multiStep.containsMatchIn(phrase)) Unmatched.MULTIPLE else Unmatched.UNSUPPORTED,
        )
    }

    companion object {
        const val MAX_COMMAND_LENGTH = 240
        private val marks = Regex("[\\u0640\\u064B-\\u065F\\u0670\\p{Cf}]")
        private val space = Regex("[\\s\\p{Zs}]+")
        private val endPunctuation = Regex("[.!?؟]+$")
        private val alefs = Regex("[أإآٱ]")
        private val dashes = Regex("[-‐‑–]")
        private val multiStep = Regex(
            "(?:^| )(?:and then|then|after that|وبعدين|وبعدها|ومن ثم|ثم|بعد كده|وبعد كده)(?: |$)|[;؛]",
        )
        private val politenessPrefixes = listOf("could you", "can you", "please", "لو سمحت", "من فضلك", "ممكن")
            .map(::normalize)
        private val politenessSuffixes = listOf("من فضلك", "لو سمحت", "please").map(::normalize)
        private val openEn = listOf("open", "launch", "show")
        private val openAr = listOf("افتح", "افتحلي", "افتح لي", "تفتح", "تفتحلي", "تفتح لي", "اعرض", "وريني")
        private val appEn = openEn + "start"
        private val appAr = openAr + listOf("شغل", "شغلي", "شغل لي", "تشغل", "تشغلي")

        private val phrases = buildMap {
            fun phrase(action: DeviceAction, value: String) {
                val key = normalize(value)
                val previous = put(key, action)
                check(previous == null || previous == action) { "Ambiguous grammar entry: $key" }
            }
            fun target(action: DeviceAction, en: List<String>, ar: List<String>, app: Boolean = false) {
                for (name in en) for (verb in if (app) appEn else openEn) phrase(action, "$verb $name")
                for (name in ar) for (verb in if (app) appAr else openAr) phrase(action, "$verb $name")
            }
            target(DeviceAction.WHATSAPP, listOf("whatsapp", "whats app"), listOf("واتساب", "الواتساب", "واتس اب", "واتس", "الواتس", "whatsapp"), app = true)
            target(DeviceAction.YOUTUBE, listOf("youtube", "you tube"), listOf("يوتيوب", "اليوتيوب", "يو توب", "youtube"), app = true)
            target(DeviceAction.INSTAGRAM, listOf("instagram"), listOf("انستجرام", "إنستغرام", "انستقرام", "الانستجرام", "إنستا", "instagram"), app = true)
            target(DeviceAction.FACEBOOK, listOf("facebook"), listOf("فيسبوك", "الفيسبوك", "فيس بوك", "الفيس", "facebook"), app = true)
            target(DeviceAction.SPOTIFY, listOf("spotify"), listOf("سبوتيفاي", "سبوتيفي", "spotify"), app = true)
            target(DeviceAction.CAMERA, listOf("camera", "the camera"), listOf("الكاميرا", "كاميرا"))
            target(DeviceAction.DIALER, listOf("dialer", "phone app", "the phone app"), listOf("لوحة الاتصال", "تطبيق الهاتف", "تطبيق التليفون", "الهاتف", "التليفون"))
            target(DeviceAction.MAPS, listOf("maps", "google maps"), listOf("الخرائط", "خرائط جوجل", "خرائط غوغل", "جوجل مابس"), app = true)
            target(DeviceAction.SETTINGS, listOf("settings", "android settings", "system settings"), listOf("الإعدادات", "اعدادات", "إعدادات النظام", "إعدادات الجهاز"))
            target(DeviceAction.WIFI, listOf("wifi settings", "wi-fi settings", "wi fi settings"), listOf("إعدادات الواي فاي", "إعدادات واي فاي", "إعدادات الوايفاي", "إعدادات wifi", "إعدادات wi-fi"))
            target(DeviceAction.BLUETOOTH, listOf("bluetooth settings"), listOf("إعدادات البلوتوث", "إعدادات بلوتوث", "إعدادات bluetooth"))
            target(DeviceAction.LOCATION, listOf("location settings"), listOf("إعدادات الموقع", "إعدادات تحديد الموقع"))
            target(DeviceAction.DISPLAY, listOf("display settings", "screen settings"), listOf("إعدادات الشاشة", "إعدادات العرض"))
            listOf(
                "battery level", "check battery", "check battery level", "show battery level",
                "what is my battery level", "what's my battery level", "how much battery is left",
                "نسبة البطارية", "مستوى البطارية", "وريني نسبة البطارية", "اعرض مستوى البطارية",
                "البطارية كام", "البطارية فاضل فيها كام", "فاضل كام في البطارية", "شوف البطارية كام",
                "شحن البطارية كام", "كام في المية البطارية", "قولي نسبة البطارية",
            ).forEach { phrase(DeviceAction.BATTERY, it) }
        }

        private fun normalize(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(marks, "")
            .replace(alefs, "ا")
            .replace('ى', 'ي').replace('ة', 'ه').replace('’', '\'')
            .replace(dashes, " ")
            .replace(space, " ")
            .trim()
            .replace(endPunctuation, "")
            .trimEnd()
    }
}
