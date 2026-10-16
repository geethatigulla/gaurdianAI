package ai.guardian.engine.detect

import ai.guardian.engine.extract.DomainUtils

/**
 * Brands and bodies commonly impersonated in Indian scams, with the domains they really use.
 * Hand-maintained and deliberately small; a missing official domain causes a false positive, so
 * add domains here rather than weakening rules.
 */
data class Brand(
    val name: String,
    /** Matches the brand being mentioned in message text (applied to lowercased text). */
    val mention: Regex,
    /** Official domains; subdomains of these are also official. */
    val officialDomains: Set<String>,
    /** Registrable labels a look-alike domain would imitate (e.g. "hdfcbank"). */
    val domainTokens: Set<String>,
    val isBank: Boolean = false,
    val isGovernment: Boolean = false,
) {
    fun isOfficialHost(host: String): Boolean {
        if (officialDomains.any { DomainUtils.isSameOrSubdomain(host, it) }) return true
        // .bank.in is restricted to RBI-verified banks; .gov.in / .nic.in to Indian government bodies.
        if (isBank && host.endsWith(".bank.in")) return true
        if (isGovernment && (host.endsWith(".gov.in") || host.endsWith(".nic.in"))) return true
        return false
    }
}

object BrandCatalog {
    private fun r(pattern: String) = Regex(pattern)

    val brands: List<Brand> = listOf(
        Brand("SBI", r("\\bsbi\\b|\\bstate bank\\b|\\byono\\b"), setOf("sbi.co.in", "onlinesbi.com", "sbicard.com", "sbi"), setOf("sbi", "onlinesbi", "sbicard", "yonosbi"), isBank = true),
        Brand("HDFC Bank", r("\\bhdfc\\b"), setOf("hdfcbank.com", "hdfc.com", "hdfcbank.net"), setOf("hdfc", "hdfcbank"), isBank = true),
        Brand("ICICI Bank", r("\\bicici\\b"), setOf("icicibank.com", "icici.com"), setOf("icici", "icicibank"), isBank = true),
        Brand("Axis Bank", r("\\baxis bank\\b"), setOf("axisbank.com", "axis.bank.in"), setOf("axisbank"), isBank = true),
        Brand("Kotak", r("\\bkotak\\b"), setOf("kotak.com", "kotakbank.com"), setOf("kotak", "kotakbank"), isBank = true),
        Brand("PNB", r("\\bpnb\\b|\\bpunjab national bank\\b"), setOf("pnbindia.in", "pnb.co.in"), setOf("pnbindia"), isBank = true),
        Brand("Bank of Baroda", r("\\bbank of baroda\\b"), setOf("bankofbaroda.in", "bankofbaroda.com"), setOf("bankofbaroda"), isBank = true),
        Brand("Paytm", r("\\bpaytm\\b"), setOf("paytm.com", "paytm.in", "paytmbank.com", "paytm.me"), setOf("paytm")),
        Brand("PhonePe", r("\\bphone ?pe\\b"), setOf("phonepe.com"), setOf("phonepe")),
        Brand("Google", r("\\bgoogle\\b|\\bgpay\\b"), setOf("google.com", "google.co.in", "g.co", "goo.gl", "googleusercontent.com", "googleapis.com", "gstatic.com", "youtube.com", "withgoogle.com"), setOf("google")),
        Brand("Amazon", r("\\bamazon\\b"), setOf("amazon.in", "amazon.com", "amzn.in", "amzn.to", "amazonaws.com", "amazonpay.in", "media-amazon.com"), setOf("amazon", "amazonpay")),
        Brand("Flipkart", r("\\bflipkart\\b"), setOf("flipkart.com", "fkrt.it", "fkrt.co"), setOf("flipkart")),
        Brand("WhatsApp", r("\\bwhats ?app\\b"), setOf("whatsapp.com", "wa.me", "whatsapp.net"), setOf("whatsapp")),
        Brand("Facebook", r("\\bfacebook\\b|\\bmeta\\b"), setOf("facebook.com", "fb.com", "fb.me", "meta.com"), setOf("facebook")),
        Brand("Instagram", r("\\binstagram\\b"), setOf("instagram.com"), setOf("instagram")),
        Brand("PayPal", r("\\bpaypal\\b"), setOf("paypal.com", "paypal.me"), setOf("paypal")),
        Brand("Netflix", r("\\bnetflix\\b"), setOf("netflix.com"), setOf("netflix")),
        Brand("Apple", r("\\bapple id\\b|\\bicloud\\b|\\bitunes\\b"), setOf("apple.com", "icloud.com"), setOf("apple", "icloud")),
        Brand("Microsoft", r("\\bmicrosoft\\b|\\boffice ?365\\b|\\boutlook\\b"), setOf("microsoft.com", "live.com", "office.com", "outlook.com", "microsoftonline.com"), setOf("microsoft", "office365")),
        Brand("Income Tax Department", r("\\bincome ?tax\\b|\\btax refund\\b|\\bitr\\b"), setOf("incometax.gov.in", "incometaxindia.gov.in"), setOf("incometax", "incometaxindia"), isGovernment = true),
        Brand("UIDAI / Aadhaar", r("\\baadhaa?r\\b|\\buidai\\b"), setOf("uidai.gov.in"), setOf("uidai", "aadhaar", "myaadhaar"), isGovernment = true),
        Brand("IRCTC", r("\\birctc\\b"), setOf("irctc.co.in", "irctc.com"), setOf("irctc")),
        Brand("India Post", r("\\bindia ?post\\b|\\bspeed ?post\\b"), setOf("indiapost.gov.in"), setOf("indiapost"), isGovernment = true),
        Brand("NPCI / BHIM", r("\\bnpci\\b|\\bbhim\\b"), setOf("npci.org.in", "bhimupi.org.in"), setOf("npci", "bhimupi")),
        Brand("TRAI", r("\\btrai\\b"), setOf("trai.gov.in"), setOf("trai"), isGovernment = true),
        Brand("EPFO", r("\\bepfo?\\b|\\bprovident fund\\b"), setOf("epfindia.gov.in"), setOf("epfindia", "epfo"), isGovernment = true),
        Brand("FedEx", r("\\bfedex\\b"), setOf("fedex.com"), setOf("fedex")),
        Brand("DHL", r("\\bdhl\\b"), setOf("dhl.com", "dhl.co.in"), setOf("dhl")),
        Brand("Blue Dart", r("\\bblue ?dart\\b"), setOf("bluedart.com"), setOf("bluedart")),
        Brand("Jio", r("\\bjio\\b"), setOf("jio.com"), setOf("jio")),
        Brand("Airtel", r("\\bairtel\\b"), setOf("airtel.in", "airtel.com"), setOf("airtel")),
    )

    /** Bodies whose name is often used to frighten victims ("digital arrest" etc). */
    val authorities: List<Pair<String, Regex>> = listOf(
        "CBI" to r("\\bcbi\\b|\\bcentral bureau of investigation\\b"),
        "Police" to r("\\bpolice\\b|\\bcyber ?(crime|cell)\\b"),
        "Customs" to r("\\bcustoms\\b"),
        "Narcotics Control Bureau" to r("\\bncb\\b|\\bnarcotics\\b"),
        "Enforcement Directorate" to r("\\benforcement directorate\\b"),
        "RBI" to r("\\brbi\\b|\\breserve bank\\b"),
        "TRAI" to r("\\btrai\\b|\\btelecom regulatory\\b"),
        "Court" to r("\\b(supreme|high|district) court\\b|\\bcourt (notice|order|summons)\\b"),
    )

    fun officialBrandFor(host: String): Brand? = brands.firstOrNull { it.isOfficialHost(host) }
}
