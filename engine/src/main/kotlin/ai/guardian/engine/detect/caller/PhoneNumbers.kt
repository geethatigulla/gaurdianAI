package ai.guardian.engine.detect.caller

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import java.util.Locale

/** Thin wrapper over libphonenumber (pure Java, offline metadata). Default region is India. */
object PhoneNumbers {
    const val DEFAULT_REGION = "IN"
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    data class Info(
        val e164: String,
        val valid: Boolean,
        val regionCode: String?,
        val countryName: String?,
        val type: PhoneNumberUtil.PhoneNumberType,
    ) {
        val isDomestic: Boolean get() = regionCode == DEFAULT_REGION
    }

    fun parse(raw: String): Info? {
        val cleaned = raw.trim().removePrefix("tel:")
        if (cleaned.count { it.isDigit() } < 3) return null
        val number: Phonenumber.PhoneNumber = try {
            util.parse(cleaned, DEFAULT_REGION)
        } catch (_: NumberParseException) {
            return null
        }
        val region = util.getRegionCodeForNumber(number)
        return Info(
            e164 = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164),
            valid = util.isValidNumber(number),
            regionCode = region,
            countryName = region?.let { Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH).ifBlank { null } },
            type = util.getNumberType(number),
        )
    }

    /** Canonical form used to store and compare reported numbers ("+919876543210"). */
    fun normalize(raw: String): String? = parse(raw)?.e164 ?: raw.filter { it.isDigit() || it == '+' }.ifBlank { null }

    /** Short codes and alphanumeric SMS headers ("VM-SBIINB", "56161") aren't phone numbers. */
    fun looksLikeSenderId(raw: String): Boolean {
        val s = raw.trim()
        return s.any { it.isLetter() } || s.count { it.isDigit() } in 1..6
    }
}
