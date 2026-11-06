package com.example.data.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TeamFlagResolver {

    data class TeamVisual(
        val code: String,
        val flagUrl: String,
        val emoji: String,
        val countryName: String,
        val fallbackFlagUrl: String = ""
    )

    private val DHAKA_TZ: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    // Map of normalized national team names and codes to (3-letter code, ISO-2 code for FlagCDN, Emoji)
    private val nationalTeamMap: Map<String, Triple<String, String, String>> = mapOf(
        // Major Cricket & Football Nations
        "india" to Triple("IND", "in", "🇮🇳"),
        "ind" to Triple("IND", "in", "🇮🇳"),
        "india women" to Triple("IND-W", "in", "🇮🇳"),
        "india w" to Triple("IND-W", "in", "🇮🇳"),
        "ind-w" to Triple("IND-W", "in", "🇮🇳"),
        "indw" to Triple("IND-W", "in", "🇮🇳"),
        "india a" to Triple("IND-A", "in", "🇮🇳"),
        "india u19" to Triple("IND", "in", "🇮🇳"),

        "bangladesh" to Triple("BAN", "bd", "🇧🇩"),
        "ban" to Triple("BAN", "bd", "🇧🇩"),
        "bgd" to Triple("BAN", "bd", "🇧🇩"),
        "bangladesh women" to Triple("BAN-W", "bd", "🇧🇩"),
        "bangladesh w" to Triple("BAN-W", "bd", "🇧🇩"),
        "ban-w" to Triple("BAN-W", "bd", "🇧🇩"),
        "banw" to Triple("BAN-W", "bd", "🇧🇩"),
        "bangladesh a" to Triple("BAN-A", "bd", "🇧🇩"),
        "bangladesh u19" to Triple("BAN", "bd", "🇧🇩"),

        "pakistan" to Triple("PAK", "pk", "🇵🇰"),
        "pak" to Triple("PAK", "pk", "🇵🇰"),
        "pakistan women" to Triple("PAK-W", "pk", "🇵🇰"),
        "pakistan w" to Triple("PAK-W", "pk", "🇵🇰"),
        "pak-w" to Triple("PAK-W", "pk", "🇵🇰"),
        "pakw" to Triple("PAK-W", "pk", "🇵🇰"),
        "pakistan a" to Triple("PAK-A", "pk", "🇵🇰"),
        "pakistan shaheens" to Triple("PAK-A", "pk", "🇵🇰"),

        "australia" to Triple("AUS", "au", "🇦🇺"),
        "aus" to Triple("AUS", "au", "🇦🇺"),
        "australia women" to Triple("AUS-W", "au", "🇦🇺"),
        "australia w" to Triple("AUS-W", "au", "🇦🇺"),
        "aus-w" to Triple("AUS-W", "au", "🇦🇺"),
        "ausw" to Triple("AUS-W", "au", "🇦🇺"),
        "australia a" to Triple("AUS-A", "au", "🇦🇺"),

        "england" to Triple("ENG", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "eng" to Triple("ENG", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "england women" to Triple("ENG-W", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "england w" to Triple("ENG-W", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "eng-w" to Triple("ENG-W", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "engw" to Triple("ENG-W", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        "england lions" to Triple("ENG-A", "gb-eng", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),

        "south africa" to Triple("RSA", "za", "🇿🇦"),
        "rsa" to Triple("RSA", "za", "🇿🇦"),
        "sa" to Triple("RSA", "za", "🇿🇦"),
        "zaf" to Triple("RSA", "za", "🇿🇦"),
        "south africa women" to Triple("RSA-W", "za", "🇿🇦"),
        "south africa w" to Triple("RSA-W", "za", "🇿🇦"),
        "sa-w" to Triple("RSA-W", "za", "🇿🇦"),
        "saw" to Triple("RSA-W", "za", "🇿🇦"),
        "rsa-w" to Triple("RSA-W", "za", "🇿🇦"),
        "south africa a" to Triple("RSA-A", "za", "🇿🇦"),

        "new zealand" to Triple("NZ", "nz", "🇳🇿"),
        "nz" to Triple("NZ", "nz", "🇳🇿"),
        "nzl" to Triple("NZ", "nz", "🇳🇿"),
        "new zealand women" to Triple("NZ-W", "nz", "🇳🇿"),
        "new zealand w" to Triple("NZ-W", "nz", "🇳🇿"),
        "nz-w" to Triple("NZ-W", "nz", "🇳🇿"),
        "nzw" to Triple("NZ-W", "nz", "🇳🇿"),
        "new zealand a" to Triple("NZ-A", "nz", "🇳🇿"),

        "sri lanka" to Triple("SL", "lk", "🇱🇰"),
        "sl" to Triple("SL", "lk", "🇱🇰"),
        "sri" to Triple("SL", "lk", "🇱🇰"),
        "lka" to Triple("SL", "lk", "🇱🇰"),
        "sri lanka women" to Triple("SL-W", "lk", "🇱🇰"),
        "sri lanka w" to Triple("SL-W", "lk", "🇱🇰"),
        "sl-w" to Triple("SL-W", "lk", "🇱🇰"),
        "slw" to Triple("SL-W", "lk", "🇱🇰"),
        "sri lanka a" to Triple("SL-A", "lk", "🇱🇰"),

        "west indies" to Triple("WI", "jm", "🌴"),
        "wi" to Triple("WI", "jm", "🌴"),
        "windies" to Triple("WI", "jm", "🌴"),
        "west indies women" to Triple("WI-W", "jm", "🌴"),
        "west indies w" to Triple("WI-W", "jm", "🌴"),
        "wi-w" to Triple("WI-W", "jm", "🌴"),
        "wiw" to Triple("WI-W", "jm", "🌴"),

        "afghanistan" to Triple("AFG", "af", "🇦🇫"),
        "afg" to Triple("AFG", "af", "🇦🇫"),
        "afghanistan a" to Triple("AFG-A", "af", "🇦🇫"),
        "afghanistan w" to Triple("AFG-W", "af", "🇦🇫"),

        "zimbabwe" to Triple("ZIM", "zw", "🇿🇼"),
        "zim" to Triple("ZIM", "zw", "🇿🇼"),
        "zwe" to Triple("ZIM", "zw", "🇿🇼"),
        "zimbabwe women" to Triple("ZIM-W", "zw", "🇿🇼"),
        "zim-w" to Triple("ZIM-W", "zw", "🇿🇼"),

        "ireland" to Triple("IRE", "ie", "🇮🇪"),
        "ire" to Triple("IRE", "ie", "🇮🇪"),
        "irl" to Triple("IRE", "ie", "🇮🇪"),
        "ireland women" to Triple("IRE-W", "ie", "🇮🇪"),
        "ire-w" to Triple("IRE-W", "ie", "🇮🇪"),

        "scotland" to Triple("SCO", "gb-sct", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
        "sco" to Triple("SCO", "gb-sct", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),
        "scotland women" to Triple("SCO-W", "gb-sct", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"),

        "netherlands" to Triple("NED", "nl", "🇳🇱"),
        "ned" to Triple("NED", "nl", "🇳🇱"),
        "nld" to Triple("NED", "nl", "🇳🇱"),
        "holland" to Triple("NED", "nl", "🇳🇱"),

        "nepal" to Triple("NEP", "np", "🇳🇵"),
        "nep" to Triple("NEP", "np", "🇳🇵"),
        "npl" to Triple("NEP", "np", "🇳🇵"),

        "uae" to Triple("UAE", "ae", "🇦🇪"),
        "united arab emirates" to Triple("UAE", "ae", "🇦🇪"),
        "are" to Triple("UAE", "ae", "🇦🇪"),

        "oman" to Triple("OMA", "om", "🇴🇲"),
        "oma" to Triple("OMA", "om", "🇴🇲"),
        "omn" to Triple("OMA", "om", "🇴🇲"),

        "usa" to Triple("USA", "us", "🇺🇸"),
        "united states" to Triple("USA", "us", "🇺🇸"),
        "us" to Triple("USA", "us", "🇺🇸"),

        "canada" to Triple("CAN", "ca", "🇨🇦"),
        "can" to Triple("CAN", "ca", "🇨🇦"),

        "namibia" to Triple("NAM", "na", "🇳🇦"),
        "nam" to Triple("NAM", "na", "🇳🇦"),

        "uganda" to Triple("UGA", "ug", "🇺🇬"),
        "uga" to Triple("UGA", "ug", "🇺🇬"),

        "papua new guinea" to Triple("PNG", "pg", "🇵🇬"),
        "png" to Triple("PNG", "pg", "🇵🇬"),

        "hong kong" to Triple("HKG", "hk", "🇭🇰"),
        "hkg" to Triple("HKG", "hk", "🇭🇰"),
        "hk" to Triple("HKG", "hk", "🇭🇰"),

        "malaysia" to Triple("MAS", "my", "🇲🇾"),
        "mas" to Triple("MAS", "my", "🇲🇾"),
        "mys" to Triple("MAS", "my", "🇲🇾"),

        "singapore" to Triple("SIN", "sg", "🇸🇬"),
        "sin" to Triple("SIN", "sg", "🇸🇬"),
        "sgp" to Triple("SIN", "sg", "🇸🇬"),

        "kuwait" to Triple("KUW", "kw", "🇰🇼"),
        "kuw" to Triple("KUW", "kw", "🇰🇼"),

        "qatar" to Triple("QAT", "qa", "🇶🇦"),
        "qat" to Triple("QAT", "qa", "🇶🇦"),

        "bahrain" to Triple("BHR", "bh", "🇧🇭"),
        "bhr" to Triple("BHR", "bh", "🇧🇭"),

        "kenya" to Triple("KEN", "ke", "🇰🇪"),
        "ken" to Triple("KEN", "ke", "🇰🇪"),

        "bermuda" to Triple("BER", "bm", "🇧🇲"),
        "ber" to Triple("BER", "bm", "🇧🇲"),

        "jersey" to Triple("JER", "je", "🇯🇪"),
        "jer" to Triple("JER", "je", "🇯🇪"),

        "tanzania" to Triple("TAN", "tz", "🇹🇿"),
        "tan" to Triple("TAN", "tz", "🇹🇿"),

        "nigeria" to Triple("NGA", "ng", "🇳🇬"),
        "nga" to Triple("NGA", "ng", "🇳🇬"),
        "ngr" to Triple("NGA", "ng", "🇳🇬"),

        // Major Football Nations
        "brazil" to Triple("BRA", "br", "🇧🇷"),
        "bra" to Triple("BRA", "br", "🇧🇷"),
        "argentina" to Triple("ARG", "ar", "🇦🇷"),
        "arg" to Triple("ARG", "ar", "🇦🇷"),
        "france" to Triple("FRA", "fr", "🇫🇷"),
        "fra" to Triple("FRA", "fr", "🇫🇷"),
        "spain" to Triple("ESP", "es", "🇪🇸"),
        "esp" to Triple("ESP", "es", "🇪🇸"),
        "germany" to Triple("GER", "de", "🇩🇪"),
        "ger" to Triple("GER", "de", "🇩🇪"),
        "deu" to Triple("GER", "de", "🇩🇪"),
        "portugal" to Triple("POR", "pt", "🇵🇹"),
        "por" to Triple("POR", "pt", "🇵🇹"),
        "prt" to Triple("POR", "pt", "🇵🇹"),
        "italy" to Triple("ITA", "it", "🇮🇹"),
        "ita" to Triple("ITA", "it", "🇮🇹"),
        "belgium" to Triple("BEL", "be", "🇧🇪"),
        "bel" to Triple("BEL", "be", "🇧🇪"),
        "croatia" to Triple("CRO", "hr", "🇭🇷"),
        "cro" to Triple("CRO", "hr", "🇭🇷"),
        "hrv" to Triple("CRO", "hr", "🇭🇷"),
        "uruguay" to Triple("URU", "uy", "🇺🇾"),
        "uru" to Triple("URU", "uy", "🇺🇾"),
        "ury" to Triple("URU", "uy", "🇺🇾"),
        "colombia" to Triple("COL", "co", "🇨🇴"),
        "col" to Triple("COL", "co", "🇨🇴"),
        "mexico" to Triple("MEX", "mx", "🇲🇽"),
        "mex" to Triple("MEX", "mx", "🇲🇽"),
        "japan" to Triple("JPN", "jp", "🇯🇵"),
        "jpn" to Triple("JPN", "jp", "🇯🇵"),
        "south korea" to Triple("KOR", "kr", "🇰🇷"),
        "korea republic" to Triple("KOR", "kr", "🇰🇷"),
        "kor" to Triple("KOR", "kr", "🇰🇷"),
        "morocco" to Triple("MAR", "ma", "🇲🇦"),
        "mar" to Triple("MAR", "ma", "🇲🇦"),
        "senegal" to Triple("SEN", "sn", "🇸🇳"),
        "sen" to Triple("SEN", "sn", "🇸🇳"),
        "egypt" to Triple("EGY", "eg", "🇪🇬"),
        "egy" to Triple("EGY", "eg", "🇪🇬"),
        "ghana" to Triple("GHA", "gh", "🇬🇭"),
        "gha" to Triple("GHA", "gh", "🇬🇭"),
        "cameroon" to Triple("CMR", "cm", "🇨🇲"),
        "cmr" to Triple("CMR", "cm", "🇨🇲"),
        "algeria" to Triple("ALG", "dz", "🇩🇿"),
        "alg" to Triple("ALG", "dz", "🇩🇿"),
        "tunisia" to Triple("TUN", "tn", "🇹🇳"),
        "tun" to Triple("TUN", "tn", "🇹🇳"),
        "ivory coast" to Triple("CIV", "ci", "🇨🇮"),
        "cote d'ivoire" to Triple("CIV", "ci", "🇨🇮"),
        "civ" to Triple("CIV", "ci", "🇨🇮"),
        "saudi arabia" to Triple("KSA", "sa", "🇸🇦"),
        "ksa" to Triple("KSA", "sa", "🇸🇦"),
        "iran" to Triple("IRN", "ir", "🇮🇷"),
        "irn" to Triple("IRN", "ir", "🇮🇷"),
        "iraq" to Triple("IRQ", "iq", "🇮🇶"),
        "irq" to Triple("IRQ", "iq", "🇮🇶"),
        "uzbekistan" to Triple("UZB", "uz", "🇺🇿"),
        "uzb" to Triple("UZB", "uz", "🇺🇿"),
        "jordan" to Triple("JOR", "jo", "🇯🇴"),
        "jor" to Triple("JOR", "jo", "🇯🇴"),
        "indonesia" to Triple("IDN", "id", "🇮🇩"),
        "idn" to Triple("IDN", "id", "🇮🇩"),
        "ina" to Triple("IDN", "id", "🇮🇩"),
        "thailand" to Triple("THA", "th", "🇹🇭"),
        "tha" to Triple("THA", "th", "🇹🇭"),
        "vietnam" to Triple("VIE", "vn", "🇻🇳"),
        "vie" to Triple("VIE", "vn", "🇻🇳"),
        "china" to Triple("CHN", "cn", "🇨🇳"),
        "chn" to Triple("CHN", "cn", "🇨🇳"),
        "switzerland" to Triple("SUI", "ch", "🇨🇭"),
        "sui" to Triple("SUI", "ch", "🇨🇭"),
        "che" to Triple("SUI", "ch", "🇨🇭"),
        "denmark" to Triple("DEN", "dk", "🇩🇰"),
        "den" to Triple("DEN", "dk", "🇩🇰"),
        "dnk" to Triple("DEN", "dk", "🇩🇰"),
        "poland" to Triple("POL", "pl", "🇵🇱"),
        "pol" to Triple("POL", "pl", "🇵🇱"),
        "sweden" to Triple("SWE", "se", "🇸🇪"),
        "swe" to Triple("SWE", "se", "🇸🇪"),
        "norway" to Triple("NOR", "no", "🇳🇴"),
        "nor" to Triple("NOR", "no", "🇳🇴"),
        "austria" to Triple("AUT", "at", "🇦🇹"),
        "aut" to Triple("AUT", "at", "🇦🇹"),
        "turkey" to Triple("TUR", "tr", "🇹🇷"),
        "turkiye" to Triple("TUR", "tr", "🇹🇷"),
        "tur" to Triple("TUR", "tr", "🇹🇷"),
        "ukraine" to Triple("UKR", "ua", "🇺🇦"),
        "ukr" to Triple("UKR", "ua", "🇺🇦"),
        "serbia" to Triple("SRB", "rs", "🇷🇸"),
        "srb" to Triple("SRB", "rs", "🇷🇸"),
        "greece" to Triple("GRE", "gr", "🇬🇷"),
        "gre" to Triple("GRE", "gr", "🇬🇷"),
        "czechia" to Triple("CZE", "cz", "🇨🇿"),
        "czech republic" to Triple("CZE", "cz", "🇨🇿"),
        "cze" to Triple("CZE", "cz", "🇨🇿"),
        "romania" to Triple("ROU", "ro", "🇷🇴"),
        "rou" to Triple("ROU", "ro", "🇷🇴"),
        "hungary" to Triple("HUN", "hu", "🇭🇺"),
        "hun" to Triple("HUN", "hu", "🇭🇺"),
        "slovakia" to Triple("SVK", "sk", "🇸🇰"),
        "svk" to Triple("SVK", "sk", "🇸🇰"),
        "slovenia" to Triple("SVN", "si", "🇸🇮"),
        "svn" to Triple("SVN", "si", "🇸🇮"),
        "wales" to Triple("WAL", "gb-wls", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
        "wal" to Triple("WAL", "gb-wls", "🏴󠁧󠁢󠁷󠁬󠁳󠁿"),
        "chile" to Triple("CHI", "cl", "🇨🇱"),
        "chi" to Triple("CHI", "cl", "🇨🇱"),
        "chl" to Triple("CHI", "cl", "🇨🇱"),
        "peru" to Triple("PER", "pe", "🇵🇪"),
        "per" to Triple("PER", "pe", "🇵🇪"),
        "ecuador" to Triple("ECU", "ec", "🇪🇨"),
        "ecu" to Triple("ECU", "ec", "🇪🇨"),
        "paraguay" to Triple("PAR", "py", "🇵🇾"),
        "par" to Triple("PAR", "py", "🇵🇾"),
        "venezuela" to Triple("VEN", "ve", "🇻🇪"),
        "ven" to Triple("VEN", "ve", "🇻🇪"),
        "bolivia" to Triple("BOL", "bo", "🇧🇴"),
        "bol" to Triple("BOL", "bo", "🇧🇴"),
        "costa rica" to Triple("CRC", "cr", "🇨🇷"),
        "crc" to Triple("CRC", "cr", "🇨🇷"),
        "panama" to Triple("PAN", "pa", "🇵🇦"),
        "pan" to Triple("PAN", "pa", "🇵🇦"),
        "jamaica" to Triple("JAM", "jm", "🇯🇲"),
        "jam" to Triple("JAM", "jm", "🇯🇲"),
        "honduras" to Triple("HON", "hn", "🇭🇳"),
        "hon" to Triple("HON", "hn", "🇭🇳")
    )

    // High-resolution official club & cricket franchise logos (ESPN CDN / Wikimedia PNG)
    private val clubLogoMap: Map<String, Pair<String, String>> = mapOf(
        // English Premier League
        "arsenal" to ("ARS" to "https://a.espncdn.com/i/teamlogos/soccer/500/359.png"),
        "chelsea" to ("CHE" to "https://a.espncdn.com/i/teamlogos/soccer/500/363.png"),
        "liverpool" to ("LIV" to "https://a.espncdn.com/i/teamlogos/soccer/500/364.png"),
        "manchester city" to ("MCI" to "https://a.espncdn.com/i/teamlogos/soccer/500/382.png"),
        "man city" to ("MCI" to "https://a.espncdn.com/i/teamlogos/soccer/500/382.png"),
        "manchester united" to ("MUN" to "https://a.espncdn.com/i/teamlogos/soccer/500/360.png"),
        "man united" to ("MUN" to "https://a.espncdn.com/i/teamlogos/soccer/500/360.png"),
        "man utd" to ("MUN" to "https://a.espncdn.com/i/teamlogos/soccer/500/360.png"),
        "tottenham hotspur" to ("TOT" to "https://a.espncdn.com/i/teamlogos/soccer/500/367.png"),
        "tottenham" to ("TOT" to "https://a.espncdn.com/i/teamlogos/soccer/500/367.png"),
        "spurs" to ("TOT" to "https://a.espncdn.com/i/teamlogos/soccer/500/367.png"),
        "newcastle united" to ("NEW" to "https://a.espncdn.com/i/teamlogos/soccer/500/361.png"),
        "newcastle" to ("NEW" to "https://a.espncdn.com/i/teamlogos/soccer/500/361.png"),
        "aston villa" to ("AVL" to "https://a.espncdn.com/i/teamlogos/soccer/500/362.png"),
        "west ham united" to ("WHU" to "https://a.espncdn.com/i/teamlogos/soccer/500/371.png"),
        "west ham" to ("WHU" to "https://a.espncdn.com/i/teamlogos/soccer/500/371.png"),
        "brighton" to ("BHA" to "https://a.espncdn.com/i/teamlogos/soccer/500/331.png"),
        "brighton & hove albion" to ("BHA" to "https://a.espncdn.com/i/teamlogos/soccer/500/331.png"),
        "everton" to ("EVE" to "https://a.espncdn.com/i/teamlogos/soccer/500/368.png"),
        "fulham" to ("FUL" to "https://a.espncdn.com/i/teamlogos/soccer/500/370.png"),
        "brentford" to ("BRE" to "https://a.espncdn.com/i/teamlogos/soccer/500/337.png"),
        "crystal palace" to ("CRY" to "https://a.espncdn.com/i/teamlogos/soccer/500/384.png"),
        "wolverhampton wanderers" to ("WOL" to "https://a.espncdn.com/i/teamlogos/soccer/500/380.png"),
        "wolves" to ("WOL" to "https://a.espncdn.com/i/teamlogos/soccer/500/380.png"),
        "nottingham forest" to ("NFO" to "https://a.espncdn.com/i/teamlogos/soccer/500/393.png"),
        "bournemouth" to ("BOU" to "https://a.espncdn.com/i/teamlogos/soccer/500/349.png"),
        "afc bournemouth" to ("BOU" to "https://a.espncdn.com/i/teamlogos/soccer/500/349.png"),
        "leicester city" to ("LEI" to "https://a.espncdn.com/i/teamlogos/soccer/500/375.png"),
        "leicester" to ("LEI" to "https://a.espncdn.com/i/teamlogos/soccer/500/375.png"),
        "southampton" to ("SOU" to "https://a.espncdn.com/i/teamlogos/soccer/500/376.png"),
        "ipswich town" to ("IPS" to "https://a.espncdn.com/i/teamlogos/soccer/500/373.png"),
        "leeds united" to ("LEE" to "https://a.espncdn.com/i/teamlogos/soccer/500/357.png"),

        // LaLiga (Spain)
        "real madrid" to ("RMA" to "https://a.espncdn.com/i/teamlogos/soccer/500/86.png"),
        "barcelona" to ("BAR" to "https://a.espncdn.com/i/teamlogos/soccer/500/83.png"),
        "fc barcelona" to ("BAR" to "https://a.espncdn.com/i/teamlogos/soccer/500/83.png"),
        "atletico madrid" to ("ATM" to "https://a.espncdn.com/i/teamlogos/soccer/500/1068.png"),
        "athletic club" to ("ATH" to "https://a.espncdn.com/i/teamlogos/soccer/500/93.png"),
        "athletic bilbao" to ("ATH" to "https://a.espncdn.com/i/teamlogos/soccer/500/93.png"),
        "real sociedad" to ("RSO" to "https://a.espncdn.com/i/teamlogos/soccer/500/89.png"),
        "real betis" to ("BET" to "https://a.espncdn.com/i/teamlogos/soccer/500/244.png"),
        "villarreal" to ("VIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/102.png"),
        "sevilla" to ("SEV" to "https://a.espncdn.com/i/teamlogos/soccer/500/243.png"),
        "valencia" to ("VAL" to "https://a.espncdn.com/i/teamlogos/soccer/500/94.png"),
        "girona" to ("GIR" to "https://a.espncdn.com/i/teamlogos/soccer/500/9812.png"),
        "celta vigo" to ("CEL" to "https://a.espncdn.com/i/teamlogos/soccer/500/85.png"),
        "osasuna" to ("OSA" to "https://a.espncdn.com/i/teamlogos/soccer/500/97.png"),
        "getafe" to ("GET" to "https://a.espncdn.com/i/teamlogos/soccer/500/98.png"),
        "mallorca" to ("MLL" to "https://a.espncdn.com/i/teamlogos/soccer/500/84.png"),
        "rayo vallecano" to ("RAY" to "https://a.espncdn.com/i/teamlogos/soccer/500/101.png"),
        "espanyol" to ("ESP" to "https://a.espncdn.com/i/teamlogos/soccer/500/88.png"),

        // Serie A (Italy)
        "inter milan" to ("INT" to "https://a.espncdn.com/i/teamlogos/soccer/500/110.png"),
        "internazionale" to ("INT" to "https://a.espncdn.com/i/teamlogos/soccer/500/110.png"),
        "inter" to ("INT" to "https://a.espncdn.com/i/teamlogos/soccer/500/110.png"),
        "ac milan" to ("MIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/103.png"),
        "milan" to ("MIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/103.png"),
        "juventus" to ("JUV" to "https://a.espncdn.com/i/teamlogos/soccer/500/111.png"),
        "napoli" to ("NAP" to "https://a.espncdn.com/i/teamlogos/soccer/500/114.png"),
        "as roma" to ("ROM" to "https://a.espncdn.com/i/teamlogos/soccer/500/104.png"),
        "roma" to ("ROM" to "https://a.espncdn.com/i/teamlogos/soccer/500/104.png"),
        "lazio" to ("LAZ" to "https://a.espncdn.com/i/teamlogos/soccer/500/112.png"),
        "atalanta" to ("ATA" to "https://a.espncdn.com/i/teamlogos/soccer/500/105.png"),
        "fiorentina" to ("FIO" to "https://a.espncdn.com/i/teamlogos/soccer/500/109.png"),
        "bologna" to ("BOL" to "https://a.espncdn.com/i/teamlogos/soccer/500/107.png"),

        // Bundesliga (Germany)
        "bayern munich" to ("BAY" to "https://a.espncdn.com/i/teamlogos/soccer/500/132.png"),
        "fc bayern munich" to ("BAY" to "https://a.espncdn.com/i/teamlogos/soccer/500/132.png"),
        "borussia dortmund" to ("BVB" to "https://a.espncdn.com/i/teamlogos/soccer/500/124.png"),
        "dortmund" to ("BVB" to "https://a.espncdn.com/i/teamlogos/soccer/500/124.png"),
        "bayer leverkusen" to ("LEV" to "https://a.espncdn.com/i/teamlogos/soccer/500/131.png"),
        "leverkusen" to ("LEV" to "https://a.espncdn.com/i/teamlogos/soccer/500/131.png"),
        "rb leipzig" to ("RBL" to "https://a.espncdn.com/i/teamlogos/soccer/500/11420.png"),
        "eintracht frankfurt" to ("SGE" to "https://a.espncdn.com/i/teamlogos/soccer/500/125.png"),
        "vfb stuttgart" to ("VFB" to "https://a.espncdn.com/i/teamlogos/soccer/500/134.png"),

        // Ligue 1 & Europe
        "paris saint-germain" to ("PSG" to "https://a.espncdn.com/i/teamlogos/soccer/500/160.png"),
        "paris saint germain" to ("PSG" to "https://a.espncdn.com/i/teamlogos/soccer/500/160.png"),
        "psg" to ("PSG" to "https://a.espncdn.com/i/teamlogos/soccer/500/160.png"),
        "marseille" to ("OM" to "https://a.espncdn.com/i/teamlogos/soccer/500/176.png"),
        "olympique marseille" to ("OM" to "https://a.espncdn.com/i/teamlogos/soccer/500/176.png"),
        "lyon" to ("OL" to "https://a.espncdn.com/i/teamlogos/soccer/500/167.png"),
        "as monaco" to ("MON" to "https://a.espncdn.com/i/teamlogos/soccer/500/174.png"),
        "monaco" to ("MON" to "https://a.espncdn.com/i/teamlogos/soccer/500/174.png"),
        "lille" to ("LIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/166.png"),
        "ajax" to ("AJA" to "https://a.espncdn.com/i/teamlogos/soccer/500/139.png"),
        "psv eindhoven" to ("PSV" to "https://a.espncdn.com/i/teamlogos/soccer/500/148.png"),
        "feyenoord" to ("FEY" to "https://a.espncdn.com/i/teamlogos/soccer/500/142.png"),
        "benfica" to ("BEN" to "https://a.espncdn.com/i/teamlogos/soccer/500/1929.png"),
        "porto" to ("POR" to "https://a.espncdn.com/i/teamlogos/soccer/500/437.png"),
        "fc porto" to ("POR" to "https://a.espncdn.com/i/teamlogos/soccer/500/437.png"),
        "sporting cp" to ("SCP" to "https://a.espncdn.com/i/teamlogos/soccer/500/2250.png"),
        "galatasaray" to ("GAL" to "https://a.espncdn.com/i/teamlogos/soccer/500/432.png"),
        "fenerbahce" to ("FEN" to "https://a.espncdn.com/i/teamlogos/soccer/500/436.png"),
        "besiktas" to ("BES" to "https://a.espncdn.com/i/teamlogos/soccer/500/1895.png"),
        "celtic" to ("CEL" to "https://a.espncdn.com/i/teamlogos/soccer/500/256.png"),
        "rangers" to ("RAN" to "https://a.espncdn.com/i/teamlogos/soccer/500/257.png"),

        // Americas & Saudi Pro League
        "inter miami" to ("MIA" to "https://a.espncdn.com/i/teamlogos/soccer/500/20232.png"),
        "inter miami cf" to ("MIA" to "https://a.espncdn.com/i/teamlogos/soccer/500/20232.png"),
        "al nassr" to ("NAS" to "https://a.espncdn.com/i/teamlogos/soccer/500/17892.png"),
        "al-nassr" to ("NAS" to "https://a.espncdn.com/i/teamlogos/soccer/500/17892.png"),
        "al hilal" to ("HIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/929.png"),
        "al-hilal" to ("HIL" to "https://a.espncdn.com/i/teamlogos/soccer/500/929.png"),
        "al ittihad" to ("ITT" to "https://a.espncdn.com/i/teamlogos/soccer/500/4322.png"),
        "al ahli" to ("AHL" to "https://a.espncdn.com/i/teamlogos/soccer/500/8331.png"),
        "boca juniors" to ("BOC" to "https://a.espncdn.com/i/teamlogos/soccer/500/5.png"),
        "river plate" to ("RIV" to "https://a.espncdn.com/i/teamlogos/soccer/500/16.png"),
        "flamengo" to ("FLA" to "https://a.espncdn.com/i/teamlogos/soccer/500/819.png"),
        "palmeiras" to ("PAL" to "https://a.espncdn.com/i/teamlogos/soccer/500/2029.png"),
        "santos" to ("SAN" to "https://a.espncdn.com/i/teamlogos/soccer/500/2674.png"),
        "corinthians" to ("COR" to "https://a.espncdn.com/i/teamlogos/soccer/500/874.png")
    )

    private val YOUTH_OR_NON_SENIOR_REGEX = Regex(
        "\\b(u-?15|u-?16|u-?17|u-?18|u-?19|u-?20|u-?21|u-?22|u-?23|under[- ]?17|under[- ]?19|under[- ]?20|under[- ]?21|under[- ]?23|youth|academy|reserves?|amateur|esports|e-sports|virtual|cyber|simulated|xi| emerging|a-team)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun normalizeTeamKey(raw: String): String {
        return raw.trim().lowercase(Locale.US)
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Converts Wikimedia SVG URLs to PNG thumbnail URLs so Android's BitmapFactory can decode them,
     * and cleans up relative or malformed URLs.
     */
    fun sanitizeFlagOrLogoUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""

        val httpsUrl = if (trimmed.startsWith("http://", ignoreCase = true)) {
            "https://" + trimmed.substring(7)
        } else if (trimmed.startsWith("//")) {
            "https:$trimmed"
        } else {
            trimmed
        }

        if (httpsUrl.contains("upload.wikimedia.org/wikipedia/", ignoreCase = true) &&
            httpsUrl.endsWith(".svg", ignoreCase = true) &&
            !httpsUrl.contains("/thumb/", ignoreCase = true)
        ) {
            val parts = httpsUrl.split("/wikipedia/")
            if (parts.size == 2) {
                val subParts = parts[1].split("/")
                if (subParts.size >= 4) {
                    val repo = subParts[0]
                    val rest = subParts.drop(1).joinToString("/")
                    val fileName = subParts.last()
                    return "${parts[0]}/wikipedia/$repo/thumb/$rest/240px-$fileName.png"
                }
            }
        }
        return httpsUrl
    }

    /**
     * Checks whether the given team name or code matches a known national team.
     */
    fun isRecognizedNationalTeam(teamName: String, teamCode: String = ""): Boolean {
        val cleanName = normalizeTeamKey(teamName)
        if (cleanName.endsWith(" fc") || cleanName.startsWith("fc ") ||
            cleanName.endsWith(" cf") || cleanName.endsWith(" sc") ||
            cleanName.contains(" united") || cleanName.contains(" city") ||
            cleanName.contains(" club") || cleanName.contains(" sporting")
        ) {
            return false
        }
        if (nationalTeamMap.containsKey(cleanName)) return true
        val stripped = cleanName
            .removeSuffix(" men")
            .removeSuffix(" women")
            .removeSuffix(" w")
            .removeSuffix(" a")
            .removeSuffix(" xi")
            .removeSuffix(" u19")
            .removeSuffix(" u-19")
            .trim()
        if (stripped.isNotBlank() && nationalTeamMap.containsKey(stripped)) return true
        val cleanCode = normalizeTeamKey(teamCode)
        return cleanCode.isNotBlank() && nationalTeamMap.containsKey(cleanCode)
    }

    /**
     * Checks whether an event is a Youth, U17-U23, Reserve, Academy, or Esports match.
     */
    fun isYouthOrNonSeniorEvent(
        team1Name: String,
        team2Name: String,
        tournamentOrLeague: String = ""
    ): Boolean {
        val combined = "$team1Name $team2Name $tournamentOrLeague"
        return YOUTH_OR_NON_SENIOR_REGEX.containsMatchIn(combined)
    }

    /**
     * Checks whether a Cricket match is a recognized Senior International Cricket fixture.
     */
    fun isSeniorInternationalCricketMatch(
        team1Name: String,
        team2Name: String,
        tournament: String = "",
        format: String = ""
    ): Boolean {
        if (isYouthOrNonSeniorEvent(team1Name, team2Name, "$tournament $format")) {
            return false
        }
        val t1Nat = isRecognizedNationalTeam(team1Name)
        val t2Nat = isRecognizedNationalTeam(team2Name)
        return t1Nat && t2Nat
    }

    /**
     * Checks whether `eventEpochMs` falls on Yesterday, Today, or Tomorrow in `Asia/Dhaka` timezone.
     */
    fun isWithinYesterdayTodayTomorrowDhaka(
        eventEpochMs: Long,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (eventEpochMs <= 0L) return false
        val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = DHAKA_TZ
        }
        val eventDay = dayFmt.format(Date(eventEpochMs))
        val oneDayMs = 24L * 3600_000L
        val validDays = setOf(
            dayFmt.format(Date(referenceNowEpochMs - oneDayMs)),
            dayFmt.format(Date(referenceNowEpochMs)),
            dayFmt.format(Date(referenceNowEpochMs + oneDayMs))
        )
        return eventDay in validDays
    }

    private fun lookupNationalTeam(teamName: String, teamCode: String = ""): Triple<String, String, String>? {
        val cleanName = normalizeTeamKey(teamName)
        if (cleanName.endsWith(" fc") || cleanName.startsWith("fc ") ||
            cleanName.endsWith(" cf") || cleanName.endsWith(" sc")
        ) {
            return null
        }
        nationalTeamMap[cleanName]?.let { return it }
        val stripped = cleanName
            .replace(Regex("\\b(men|women|w|a|xi|u-?19|u-?17|u-?20|u-?21|u-?23|under[- ]?19|emerging|shaheens|lions)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (stripped.isNotBlank()) {
            nationalTeamMap[stripped]?.let { return it }
        }
        val cleanCode = normalizeTeamKey(teamCode)
        if (cleanCode.isNotBlank()) {
            nationalTeamMap[cleanCode]?.let { return it }
        }
        return null
    }

    private fun lookupClubLogo(teamName: String): Pair<String, String>? {
        val cleanName = normalizeTeamKey(teamName)
        clubLogoMap[cleanName]?.let { return it }
        val stripped = cleanName
            .removePrefix("fc ")
            .removeSuffix(" fc")
            .removeSuffix(" cf")
            .removeSuffix(" sc")
            .trim()
        if (stripped.isNotBlank()) {
            clubLogoMap[stripped]?.let { return it }
        }
        return null
    }

    /**
     * Resolves team badge strictly separating National Teams from Club Teams:
     * - For National Teams (`isNationalTeam == true` or recognized country name): resolves real FlagCDN PNG flag (`https://flagcdn.com/w160/<code>.png`).
     * - For Club Teams (`isNationalTeam == false`): uses official `existingFlag` logo URL or verified `clubLogoMap` crest,
     *   and NEVER falls back to a country flag when a synthetic club like `"England FC"` has no logo.
     */
    fun resolveForMatch(
        teamName: String,
        teamCode: String = "",
        existingFlag: String = "",
        isNationalTeam: Boolean = true
    ): TeamVisual {
        val derivedCode = teamCode.trim().ifBlank {
            teamName.trim().split(" ")
                .filter { it.isNotBlank() }
                .let { words ->
                    if (words.size >= 2) {
                        words.take(3).joinToString("") { it.take(1) }.uppercase(Locale.US)
                    } else {
                        teamName.trim().take(3).uppercase(Locale.US)
                    }
                }
        }.ifBlank { "TM" }

        val sanitizedExisting = sanitizeFlagOrLogoUrl(existingFlag)
        val hasValidExistingUrl = sanitizedExisting.startsWith("http://", ignoreCase = true) ||
            sanitizedExisting.startsWith("https://", ignoreCase = true)

        val matchedCountry = lookupNationalTeam(teamName, teamCode)
        val matchedClub = lookupClubLogo(teamName)

        if (!isNationalTeam && matchedCountry == null) {
            val primaryLogo = when {
                hasValidExistingUrl -> sanitizedExisting
                matchedClub != null -> matchedClub.second
                else -> ""
            }
            val fallbackLogo = if (hasValidExistingUrl && matchedClub != null && matchedClub.second != sanitizedExisting) {
                matchedClub.second
            } else {
                ""
            }
            return TeamVisual(
                code = matchedClub?.first ?: derivedCode,
                flagUrl = primaryLogo,
                emoji = "",
                countryName = teamName,
                fallbackFlagUrl = fallbackLogo
            )
        }

        if (!isNationalTeam && !isRecognizedNationalTeam(teamName, "")) {
            val primaryLogo = when {
                hasValidExistingUrl -> sanitizedExisting
                matchedClub != null -> matchedClub.second
                else -> ""
            }
            return TeamVisual(
                code = matchedClub?.first ?: derivedCode,
                flagUrl = primaryLogo,
                emoji = "",
                countryName = teamName,
                fallbackFlagUrl = matchedClub?.second.orEmpty()
            )
        }

        if (matchedCountry != null) {
            val (code3, iso2, emoji) = matchedCountry
            val flagCdnUrl = "https://flagcdn.com/w160/$iso2.png"
            val isExistingSvg = sanitizedExisting.endsWith(".svg", ignoreCase = true)
            val primaryUrl = if (hasValidExistingUrl && !isExistingSvg) sanitizedExisting else flagCdnUrl
            val secondaryUrl = if (primaryUrl != flagCdnUrl) flagCdnUrl else ""
            return TeamVisual(
                code = teamCode.trim().ifBlank { code3 },
                flagUrl = primaryUrl,
                emoji = emoji,
                countryName = teamName,
                fallbackFlagUrl = secondaryUrl
            )
        }

        val fallbackEmoji = if (existingFlag.isNotBlank() && !hasValidExistingUrl) existingFlag else "🏳️"
        return TeamVisual(
            code = derivedCode,
            flagUrl = if (hasValidExistingUrl) sanitizedExisting else "",
            emoji = fallbackEmoji,
            countryName = teamName,
            fallbackFlagUrl = matchedClub?.second.orEmpty()
        )
    }

    fun resolve(teamName: String, teamCode: String = "", existingFlag: String = ""): TeamVisual {
        return resolveForMatch(
            teamName = teamName,
            teamCode = teamCode,
            existingFlag = existingFlag,
            isNationalTeam = true
        )
    }
}
