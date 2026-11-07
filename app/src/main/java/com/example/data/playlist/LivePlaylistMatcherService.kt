package com.example.data.playlist

import android.util.Base64
import com.example.data.model.CategoryItem
import com.example.data.model.HighlightItem
import com.example.data.model.LiveMatch
import com.example.data.model.MatchStatus
import com.example.data.model.ProviderSyncDiagnostics
import com.example.data.model.SportType
import com.example.data.model.SportsChannel
import com.example.data.model.StreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class PlaylistEntry(
    val id: String,
    val name: String,
    val cleanName: String,
    val url: String,
    val quality: String,
    val logoUrl: String = "",
    val category: String = "Sports",
    val isBackup: Boolean = false,
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val drmLicense: String? = null
)

data class PlayzServerCatalog(
    val matches: List<LiveMatch> = emptyList(),
    val channels: List<SportsChannel> = emptyList(),
    val categories: List<CategoryItem> = emptyList(),
    val highlights: List<HighlightItem> = emptyList()
)

/**
 * Real-Time Auto-Updating Sports M3U Playlist, PlayZ Server (`pabbdyzone.shop`),
 * & Match-to-Broadcaster Resolver.
 */
class LivePlaylistMatcherService(
    baseClient: OkHttpClient? = null
) {
    companion object {
        val DEFAULT_PLAYLIST_URLS = listOf(
            "https://raw.githubusercontent.com/doms9/iptv/refs/heads/default/M3U8/events.m3u8",
            "https://raw.githubusercontent.com/abusaeeidx/BDxTV/refs/heads/main/playlist_s.m3u",
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
            "https://iptv-org.github.io/iptv/countries/bd.m3u",
            "https://iptv-org.github.io/iptv/countries/in.m3u",
            "https://iptv-org.github.io/iptv/countries/pk.m3u"
        )

        val PLAYZ_BASE_HOSTS = listOf(
            "https://pabbdyzone.shop/",
            "https://red-field-c5ad.ladle-meew-fky.workers.dev/https://pabbdyzone.shop/",
            "https://tourniquest.site/",
            "https://late-star-9f28.test33644.workers.dev/https://tourniquest.site/"
        )

        private const val PX_AES_KEY = "cz14RStkN01PVE5w"
        private const val PX_AES_IV = "WTlEvckd2UR41sdk"
        private const val SUB_AES_KEY = "b/1jml5nk4x5k7pN"
        private const val SUB_AES_IV = "14nMk8mN5Kl5KL7l"
        private const val SUB_SRC = "fFgGjJkKaApPbBmMoOzZeEnNcCdDrRqQtTvVuUxXhHiIwWyYlLsS"
        private const val SUB_DST = "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ"

        private const val PLAYLIST_CACHE_TTL_MS = 10 * 60_000L
        private val DHAKA_TZ = TimeZone.getTimeZone("Asia/Dhaka")

        val VERIFIED_LIVE_SEED_ENTRIES: List<PlaylistEntry> = listOf(
            // --- Cricket & South Asia Multi-Sport Channels ---
            PlaylistEntry(
                id = "pl-tsports-ayna",
                name = "T Sports HD",
                cleanName = "t sports",
                url = "https://tvsen5.aynaott.com/TnMn5kZz8aLmjklkk/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-tsports-robitv",
                name = "T Sports HD (Server 2)",
                cleanName = "t sports 2",
                url = "https://robitv.com/rn/http://45.117.61.162/01_Tsports_HD/tracks-v1a1/mono.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-tsports-bd",
                name = "T Sports HD (Server 3)",
                cleanName = "t sports 3",
                url = "https://live-us1.thaimomo.com/live-as/chtsport-1/playlist.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-asports-hd",
                name = "A Sports HD",
                cleanName = "a sports",
                url = "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/tracks-v1a1/mono.ts.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-asports-main",
                name = "A Sports (Server 2)",
                cleanName = "a sports 2",
                url = "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-ptv-sports",
                name = "PTV Sports HD",
                cleanName = "ptv sports",
                url = "https://flussonic.defineatoz.com/PTV/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-star-sports-2-ayna",
                name = "Star Sports 2 HD",
                cleanName = "star sports 2",
                url = "https://tvsen7.aynaott.com/ssport2hd/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-star-sports-2-bkp",
                name = "Star Sports 2 (Backup)",
                cleanName = "star sports 2 backup",
                url = "http://103.151.60.162:2122/play/a00v/index.m3u8?hls",
                quality = "HD",
                category = "Cricket",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-willow-tv-ayna",
                name = "Willow TV HD",
                cleanName = "willow tv",
                url = "https://tvsen5.aynaott.com/willowhd/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-willow-sports-user",
                name = "Willow Sports",
                cleanName = "willow sports",
                url = "https://warm-caverns-48629-92fab798385f.herokuapp.com/https://d36r8jifhgsk5j.cloudfront.net/Willow_TV540p.m3u8",
                quality = "720p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-willow-sofast",
                name = "Willow / Cricket Gold FHD",
                cleanName = "willow hd backup",
                url = "https://streams2.sofast.tv/ptnr-yupptv/title-cricketgold/v1/master/611d79b11b77e2f571934fd80ca1413453772ac7/b2048bb8-1686-4432-aa50-647245383e0c/manifest.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-cricket-gold-ayna",
                name = "Cricket Gold HD",
                cleanName = "cricket gold",
                url = "https://tvsen6.aynaott.com/M2W2UR49dmeKbZnmdRzN/index.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-sony-sports-1-gp",
                name = "Sony Sports 1 HD",
                cleanName = "sony sports 1",
                url = "https://drk6xq0vhn.gpcdn.net/live/ten_1_hd_720/index.m3u8",
                quality = "720p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-sony-sports-2-gp",
                name = "Sony Sports 2 HD",
                cleanName = "sony sports 2",
                url = "https://drk6xq0vhn.gpcdn.net/live/ten_2_hd_720/index.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-sony-sports-5-gp",
                name = "Sony Sports 5 HD",
                cleanName = "sony sports 5",
                url = "https://drk6xq0vhn.gpcdn.net/live/ten_5_hd_720/index.m3u8",
                quality = "720p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-sony-ten-1",
                name = "Sony Sports Ten 1",
                cleanName = "sony sports ten 1",
                url = "http://103.151.60.162:2122/play/a01c/index.m3u8?hls",
                quality = "HD",
                category = "Sports",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-sony-ten-2",
                name = "Sony Sports Ten 2",
                cleanName = "sony sports ten 2",
                url = "http://103.151.60.162:2122/play/a01b/index.m3u8?hls",
                quality = "HD",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-sony-ten-3",
                name = "Sony Sports Ten 3 Hindi",
                cleanName = "sony sports ten 3",
                url = "https://d3qs3d2rkhfqrt.cloudfront.net/out/v1/b17adfe543354fdd8d189b110617cddd/index.m3u8",
                quality = "HD",
                category = "Sports",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-live-sports-star",
                name = "Live Sports HD",
                cleanName = "live sports",
                url = "https://starsportshindiii.pages.dev/720p.m3u8",
                quality = "720p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-dd-sports-hd",
                name = "DD Sports HD",
                cleanName = "dd sports",
                url = "https://cdn.pishow.tv/ott/live/1161/master.m3u8",
                quality = "1080p",
                category = "Cricket"
            ),
            PlaylistEntry(
                id = "pl-ten-cricket",
                name = "Ten Cricket Live",
                cleanName = "ten cricket",
                url = "http://103.151.60.162:2122/play/a0fj/index.m3u8?hls",
                quality = "HD",
                category = "Cricket",
                isBackup = true
            ),

            // --- Global Football & Multi-Sport Channels ---
            PlaylistEntry(
                id = "pl-bein-xtra-1080",
                name = "Bein Sports Xtra HD",
                cleanName = "bein sports xtra hd",
                url = "https://bein-esp-xumo.amagi.tv/playlistR1080p.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-bein-xtra-720",
                name = "beIN SPORTS XTRA",
                cleanName = "bein sports xtra",
                url = "https://bein-esp-xumo.amagi.tv/playlistR720P.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-bein-xtra-es",
                name = "beIN Sports XTRA en Espanol",
                cleanName = "bein sports xtra en espanol",
                url = "https://dc1644a9jazgj.cloudfront.net/beIN_Sports_Xtra_Espanol.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-bein-sport-1",
                name = "Bein Sport 1",
                cleanName = "bein sport 1",
                url = "http://ua102.online24.pm:8222/1101/video.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-bein-xtra-amagi",
                name = "BeIN Sports Extra",
                cleanName = "bein sports extra",
                url = "https://bein-xtra-bein.amagi.tv/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-ayna",
                name = "ESPN HD",
                cleanName = "espn",
                url = "https://tvsen5.aynaott.com/espn/index.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-bkp1",
                name = "ESPN [Backup 1]",
                cleanName = "espn backup 1",
                url = "http://181.78.197.59:8000/play/a07z/index.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-telemundo-deportes",
                name = "Telemundo Deportes",
                cleanName = "telemundo deportes",
                url = "https://tda-nnaa-drct-pa6dy.fast.nbcuni.com/live/master_2.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-tyc-sports",
                name = "TyC Sports",
                cleanName = "tyc sports",
                url = "https://m.mxonlive.xyz/nexusstream/tycsport.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-tsn-2",
                name = "TSN 2 HD",
                cleanName = "tsn 2",
                url = "https://tvsen7.aynascope.net/tsn2/index.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-tsn-3",
                name = "TSN 3 HD",
                cleanName = "tsn 3",
                url = "https://tvsen7.aynascope.net/tsn3/index.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-fox-sports",
                name = "FOX Sports",
                cleanName = "fox sports",
                url = "http://45.5.118.152:8000/play/a08x/index.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-fox-sports-1",
                name = "Fox Sports 1",
                cleanName = "fox sports 1",
                url = "http://85.237.89.160:9590/usa-s/FOX-SPORTS-1/index.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-fox-sports-501",
                name = "Fox Sports 501",
                cleanName = "fox sports 501",
                url = "https://raw.githubusercontent.com/stablesports711-hue/stable-sports-movie/refs/heads/main/IPTV/FOX501HD.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-fubo-sports",
                name = "Fubo Sports",
                cleanName = "fubo sports",
                url = "https://wurlfubo.global.transmit.live/hls/67f42332d3dd98936978f581/v1/fubotv_fubo_sports_network_1/lg_us/latest/main/hls/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-nbc-sports",
                name = "NBC Sports",
                cleanName = "nbc sports",
                url = "https://xumo-xumoent-vc-122-sjv70.fast.nbcuni.com/live/master.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-aspor-tr",
                name = "A SPOR",
                cleanName = "a spor",
                url = "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/aspor/aspor_480p.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-ktv-sport-plus",
                name = "KTV Sport Plus",
                cleanName = "ktv sport plus",
                url = "https://kwtsplta.cdn.mangomolo.com/spl/smil:spl.stream.smil/chunklist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-saudi-sports",
                name = "Saudia Arabia Sports",
                cleanName = "saudia arabia",
                url = "https://shd-gcp-live.edgenextcdn.net/live/bitmovin-saudi-tv/2ad66056b51fd8c1b624854623112e43/index.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-rmtv-en",
                name = "RealMadrid TV",
                cleanName = "real madrid tv",
                url = "https://rmtv.akamaized.net/hls/live/2043154/rmtv-en-web/bitrate_3.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-talk-sport",
                name = "Talk Sport",
                cleanName = "talk sport",
                url = "https://tvsen6.aynaott.com/talkSPORT/index.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-trace-sport-stars",
                name = "Trace Sport Stars",
                cleanName = "trace sport stars",
                url = "https://lightning-tracesport-samsungau.amagi.tv/playlist.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-xtream-sports",
                name = "Xtream Sports",
                cleanName = "xtream sports",
                url = "https://streams2.sofast.tv/ptnr-yupptv/title-XTREM-SPORTS-ENG_yupptv/v1/master/611d79b11b77e2f571934fd80ca1413453772ac7/e0b81a5c-6ab5-48cd-aaa9-f82de4ab5bf9/manifest.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-xtrem-sports-2",
                name = "Xtrem Sports",
                cleanName = "xtrem sports",
                url = "https://streams2.sofast.tv/v1/master/611d79b11b77e2f571934fd80ca1413453772ac7/e0b81a5c-6ab5-48cd-aaa9-f82de4ab5bf9/manifest.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-fight-network",
                name = "Fight Network",
                cleanName = "fight network",
                url = "https://amg00966-amg00966c10-amgplt0201.playout.now3.amagi.tv/ts-eu-w1-n2/playlist/amg00966-amg00966c10-amgplt0201/playlist.m3u8",
                quality = "1080p",
                category = "Fighting"
            ),
            PlaylistEntry(
                id = "pl-motor-vision",
                name = "Motor Vision",
                cleanName = "motor vision",
                url = "https://mvg-mv-xumo.otteravision.com/mvg/mv/mv.m3u8",
                quality = "1080p",
                category = "Motorsport"
            ),
            PlaylistEntry(
                id = "pl-redbull-tv",
                name = "Red Bull TV",
                cleanName = "red bull tv",
                url = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                quality = "1080p",
                category = "Motorsport"
            ),
            PlaylistEntry(
                id = "pl-golf-channel",
                name = "Golf Channel",
                cleanName = "golf channel",
                url = "https://tvsen6.aynaott.com/golfchannel/index.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-golf-network",
                name = "Golf Network",
                cleanName = "golf network",
                url = "https://streams2.sofast.tv/ptnr-sportstribal/genre-Sports/lang-English/ctgr-Sport/title-Golf_Network/sofastplayout/b7500c3f-bd94-4725-abfd-2644e004132c/manifest.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-marquee-sports",
                name = "Marquee Sports Network",
                cleanName = "marquee sports network",
                url = "https://tvsen6.aynaott.com/MarqueeSportsNetwork/index.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-sports-grid",
                name = "Sports Grid",
                cleanName = "sports grid",
                url = "https://tvsen6.aynaott.com/SportsGrid/index.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-nfl-network",
                name = "NFL Network",
                cleanName = "nfl network",
                url = "https://tvsen6.aynaott.com/nfl/index.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-nhl-network",
                name = "NHL Network",
                cleanName = "nhl network",
                url = "https://nhl-firetv.amagi.tv/playlist.m3u8",
                quality = "1080p",
                category = "Sports"
            ),
            PlaylistEntry(
                id = "pl-star-select-1",
                name = "Star Sports Select 1 HD",
                cleanName = "star sports select 1",
                url = "http://103.151.60.162:2122/play/a026/index.m3u8?hls",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-star-select-2",
                name = "Star Sports Select 2 HD",
                cleanName = "star sports select 2",
                url = "http://103.151.60.162:2122/play/a027/index.m3u8?hls",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-cbs-golazo",
                name = "CBS Sports Golazo HD",
                cleanName = "cbs sports golazo",
                url = "https://jmp2.uk/plu-63a0e33a45264d000850ed7e.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-us-hd",
                name = "ESPN HD (US)",
                cleanName = "espn hd",
                url = "https://jmp2.uk/plu-63a0e33a45264d000850ed7e.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn2-us-hd",
                name = "ESPN 2 HD",
                cleanName = "espn 2",
                url = "https://jmp2.uk/plu-5e9f2c05172a0f0007db4786.m3u8",
                quality = "720p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-latam",
                name = "ESPN Internacional FHD",
                cleanName = "espn latin america",
                url = "https://d63fabad.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWVzX0ZJRkFQbHVzU3BhbmlzaF9ITFM/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-premium-arg",
                name = "ESPN Premium FHD",
                cleanName = "espn premium",
                url = "https://d63fabad.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWVzX0ZJRkFQbHVzU3BhbmlzaF9ITFM/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-espn-brazil",
                name = "ESPN Brasil FHD",
                cleanName = "espn brazil",
                url = "https://d2w9q46ikgrcwx.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-of5cbk3sav3w5/v1/sysdata_s_p_a_fifa_7/samsungheadend_us/latest/main/hls/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-sportitalia",
                name = "Sportitalia FHD",
                cleanName = "sportitalia",
                url = "https://edge-001.streamup.eu/sportitalia/sihd_abr/playlist.m3u8",
                quality = "1080p",
                category = "Football"
            ),
            PlaylistEntry(
                id = "pl-fifa-plus-en",
                name = "FIFA+ Global HD",
                cleanName = "fifa+",
                url = "https://d2w9q46ikgrcwx.cloudfront.net/v1/master/3722c60a815c199d9c0ef36c5b73da68a62b09d1/cc-of5cbk3sav3w5/v1/sysdata_s_p_a_fifa_7/samsungheadend_us/latest/main/hls/playlist.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-fifa-plus-es",
                name = "FIFA+ Spain HD",
                cleanName = "fifa+ spain",
                url = "https://d63fabad.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWVzX0ZJRkFQbHVzU3BhbmlzaF9ITFM/playlist.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-fifa-plus-it",
                name = "FIFA+ Italy HD",
                cleanName = "fifa+ italy",
                url = "https://5d95f7d7.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWl0X0ZJRkFQbHVzSXRhbGlhbl9ITFM/playlist.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-fifa-plus-de",
                name = "FIFA+ Germany HD",
                cleanName = "fifa+ german",
                url = "https://4397879b.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWRlX0ZJRkFQbHVzR2VybWFuX0hMUw/playlist.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            ),
            PlaylistEntry(
                id = "pl-fifa-plus-fr",
                name = "FIFA+ France HD",
                cleanName = "fifa+ french",
                url = "https://37b4c228.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/UmFrdXRlblRWLWZyX0ZJRkFQbHVzRnJlbmNoX0hMUw/playlist.m3u8",
                quality = "720p",
                category = "Football",
                isBackup = true
            )
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val dynamicEntriesByKey = ConcurrentHashMap<String, PlaylistEntry>()
    private val adminCustomEntries = ConcurrentHashMap<String, PlaylistEntry>()
    private val streamHealthMap = ConcurrentHashMap<String, Boolean>()

    // PlayZ Server (`pabbdyzone.shop`) cached match & channel records
    private val playzMatchesById = ConcurrentHashMap<String, LiveMatch>()
    private val playzMatchPointerById = ConcurrentHashMap<String, String>()
    private val playzResolvedStreamsByPointer = ConcurrentHashMap<String, List<StreamSource>>()
    private val playzResolvedTimestampByPointer = ConcurrentHashMap<String, Long>()
    private val m3uEventEntries = ConcurrentHashMap<String, PlaylistEntry>()

    @Volatile
    private var activePlayzBaseUrl: String = PLAYZ_BASE_HOSTS.first()

    @Volatile
    private var lastPlaylistSyncEpochMs: Long = 0L

    @Volatile
    private var lastPlaylistError: String? = null

    @Volatile
    private var totalDiscoveredStreams: Int = VERIFIED_LIVE_SEED_ENTRIES.size

    private val httpClient: OkHttpClient = (baseClient?.newBuilder() ?: OkHttpClient.Builder())
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val probeClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    init {
        for (seed in VERIFIED_LIVE_SEED_ENTRIES) {
            dynamicEntriesByKey[seed.cleanName] = seed
        }
    }

    /**
     * Decrypts PlayZ `.txt` payload using `Px.app` method:
     * Base64 decode -> byte-pair swap -> reverse -> Base64 decode -> AES/CBC/PKCS5Padding
     * with fallback to substitution cipher (`Lsc/a.b`).
     */
    fun decryptPlayzPayload(rawText: String): String? {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.startsWith("[") || cleaned.startsWith("{")) return cleaned

        // Method 1: Px.app (Base64 -> Swap adjacent bytes -> Reverse -> Base64 -> AES)
        runCatching {
            val b1 = Base64.decode(cleaned, Base64.DEFAULT)
            for (i in 0 until b1.size - 1 step 2) {
                val tmp = b1[i]
                b1[i] = b1[i + 1]
                b1[i + 1] = tmp
            }
            b1.reverse()
            val cipherBytes = Base64.decode(b1, Base64.DEFAULT)
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(PX_AES_KEY.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(PX_AES_IV.toByteArray(Charsets.UTF_8))
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val out = String(cipher.doFinal(cipherBytes), Charsets.UTF_8).trim()
            if (out.startsWith("[") || out.startsWith("{")) return out
        }

        // Method 2: Substitution Cipher (`Lsc/a.b`) + Base64 + optional AES-1
        runCatching {
            val sb = StringBuilder(cleaned.length)
            for (ch in cleaned) {
                val idx = SUB_SRC.indexOf(ch)
                sb.append(if (idx >= 0) SUB_DST[idx] else ch)
            }
            val decodedBytes = Base64.decode(sb.toString(), Base64.DEFAULT)
            val directStr = String(decodedBytes, Charsets.UTF_8).trim()
            if (directStr.startsWith("[") || directStr.startsWith("{")) return directStr

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(SUB_AES_KEY.toByteArray(Charsets.UTF_8), "AES")
            val ivSpec = IvParameterSpec(SUB_AES_IV.toByteArray(Charsets.UTF_8))
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            val out = String(cipher.doFinal(decodedBytes), Charsets.UTF_8).trim()
            if (out.startsWith("[") || out.startsWith("{")) return out
        }

        return null
    }

    private fun fetchPlayzEndpointDecrypted(relativePath: String): String? {
        val cleanPath = relativePath.trim()
            .removePrefix("https://pabbdyzone.shop/")
            .removePrefix("http://pabbdyzone.shop/")
            .removePrefix("https://tourniquest.site/")
            .removePrefix("http://tourniquest.site/")
            .removePrefix("/")
        if (cleanPath.isBlank()) return null

        val candidateBases = buildList {
            add(activePlayzBaseUrl)
            for (b in PLAYZ_BASE_HOSTS) {
                if (b != activePlayzBaseUrl) add(b)
            }
        }

        for (base in candidateBases) {
            try {
                val url = if (cleanPath.startsWith("http://", true) || cleanPath.startsWith("https://", true)) {
                    cleanPath
                } else {
                    base + cleanPath
                }
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string().orEmpty()
                        val dec = decryptPlayzPayload(raw)
                        if (!dec.isNullOrBlank()) {
                            activePlayzBaseUrl = base
                            return dec
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    /**
     * Fetches and decrypts the dedicated stream list (`pro/<id>.txt`, `channels/<id>.txt`, or `prohighighlts/<id>.txt`)
     * for a specific match or channel.
     */
    suspend fun resolvePlayzStreamsForPointer(
        pointerPath: String,
        matchIdPrefix: String = "pz",
        forceRefresh: Boolean = false
    ): List<StreamSource> = withContext(Dispatchers.IO) {
        val cleanPointer = pointerPath.trim().removePrefix("/")
        if (cleanPointer.isBlank()) return@withContext emptyList()

        if (!forceRefresh) {
            val cached = playzResolvedStreamsByPointer[cleanPointer]
            val cachedAt = playzResolvedTimestampByPointer[cleanPointer] ?: 0L
            val hasShortLivedToken = cached?.any {
                it.url.contains("?token=", ignoreCase = true) ||
                    it.url.contains("&token=", ignoreCase = true) ||
                    it.url.contains("hdnea=", ignoreCase = true)
            } == true
            val ttlMs = if (hasShortLivedToken) 90_000L else 5 * 60_000L
            if (!cached.isNullOrEmpty() && (System.currentTimeMillis() - cachedAt) < ttlMs) {
                return@withContext cached
            }
        }

        val dec = fetchPlayzEndpointDecrypted(cleanPointer) ?: return@withContext emptyList()
        val parsed = parsePlayzStreamItemsJson(dec, matchIdPrefix)
        if (parsed.isNotEmpty()) {
            playzResolvedStreamsByPointer[cleanPointer] = parsed
            playzResolvedTimestampByPointer[cleanPointer] = System.currentTimeMillis()
        } else {
            playzResolvedStreamsByPointer.remove(cleanPointer)
            playzResolvedTimestampByPointer.remove(cleanPointer)
        }
        parsed
    }

    private val tokenResolveClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Ported from `sd5yox.apk` (`Lsc/c->d`):
     * Decodes reversed Base64 + `|` + Caesar cipher shift used by `"type": "sp"` token endpoints.
     */
    private fun decodeSpShiftedToken(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return decodeWorkerPlaybackUrl(trimmed)
        }
        return runCatching {
            val reversedBase64 = trimmed.reversed()
            val decoded = String(Base64.decode(reversedBase64, Base64.DEFAULT), Charsets.UTF_8)
            if (!decoded.contains("|")) return@runCatching decodeWorkerPlaybackUrl(trimmed)
            val parts = decoded.split("|", limit = 2)
            val shiftedUrl = parts[0]
            val shift = parts[1].trim().toIntOrNull() ?: return@runCatching decodeWorkerPlaybackUrl(shiftedUrl)
            val sb = StringBuilder(shiftedUrl.length)
            for (ch in shiftedUrl) {
                sb.append((ch.code - shift).toChar())
            }
            val result = sb.toString().trim()
            if (result.startsWith("http://", true) || result.startsWith("https://", true)) {
                decodeWorkerPlaybackUrl(result)
            } else {
                decodeWorkerPlaybackUrl(trimmed)
            }
        }.getOrDefault(decodeWorkerPlaybackUrl(trimmed))
    }

    /**
     * Ported from `sd5yox.apk` (`Lha/b->i`):
     * Extracts Clappr `player.load({ source: fn() })` where `fn()` returns `(["h","t","t","p",...]).join("")`.
     */
    private fun extractHtmlClapprJoinUrl(htmlBody: String): String? {
        return runCatching {
            val loadFnMatch = Regex("""\.load\s*\(\s*\{\s*source\s*:\s*([a-zA-Z0-9_$]+)\s*\(\s*\)""")
                .find(htmlBody)
            val fnName = loadFnMatch?.groupValues?.getOrNull(1)
            val arrayPattern = if (!fnName.isNullOrBlank()) {
                Regex("""function\s+${Regex.escape(fnName)}\s*\(\s*\)\s*\{[\s\S]*?return\s*\(\s*\[([^\]]+)\]\s*\)\.join""")
            } else {
                Regex("""return\s*\(\s*\[([^\]]+)\]\s*\)\.join\s*\(\s*["']{2}\s*\)""")
            }
            val rawArrayContent = arrayPattern.find(htmlBody)?.groupValues?.getOrNull(1) ?: return null
            val url = rawArrayContent
                .replace("\"", "")
                .replace("'", "")
                .replace(",", "")
                .replace("\\/", "/")
                .replace("\\", "")
                .replace("\n", "")
                .replace("\r", "")
                .trim()
            url.takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }
        }.getOrNull()
    }

    private fun resolvePlayzWorkerTokenUrl(
        workerApiUrl: String,
        linkKey: String = "playback_url",
        tokenType: String = ""
    ): String? {
        val cleanApi = workerApiUrl.trim().substringBefore("|").trim().removeSuffix("?")
        if (!cleanApi.startsWith("http://", true) && !cleanApi.startsWith("https://", true)) return null
        return try {
            val originMatch = Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
                .find(cleanApi)?.groupValues?.getOrNull(1)
            val reqBuilder = Request.Builder()
                .url(cleanApi)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
            if (!originMatch.isNullOrBlank()) {
                reqBuilder.header("Referer", "$originMatch/")
            }
            tokenResolveClient.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val contentType = resp.header("Content-Type", "").orEmpty().lowercase(Locale.US)
                // If the URL itself is already a direct video/HLS stream, return it immediately
                if (contentType.contains("mpegurl") ||
                    contentType.contains("video/") ||
                    contentType.contains("dash+xml") ||
                    contentType.contains("octet-stream")
                ) {
                    return workerApiUrl.trim()
                }
                val bodyStr = resp.body?.string()?.trim().orEmpty()
                if (bodyStr.startsWith("#EXTM3U", ignoreCase = true)) {
                    return workerApiUrl.trim()
                }

                // 1. Handle `"type": "yt"` (`Lsc/d->d` in sd5yox.apk): extract `"hlsManifestUrl":"...m3u8"`
                if (tokenType.equals("yt", ignoreCase = true) || bodyStr.contains("\"hlsManifestUrl\"")) {
                    val ytM3u8 = Regex(""""hlsManifestUrl"\s*:\s*"(https?:[^"]+?\.m3u8[^"]*)"""")
                        .find(bodyStr)?.groupValues?.getOrNull(1)
                        ?.replace("\\/", "/")
                        ?.replace("\\u0026", "&")
                    if (!ytM3u8.isNullOrBlank()) {
                        return ytM3u8
                    }
                }

                // 2. Handle JSON response (`"type": "sp"` or standard JSON token API)
                if (bodyStr.startsWith("{")) {
                    val json = JSONObject(bodyStr)
                    val rawPlayback = json.optString(linkKey.ifBlank { "playback_url" }, "")
                        .ifBlank { json.optString("playback_url", "") }
                        .ifBlank { json.optString("url", "") }
                        .ifBlank { json.optString("file", "") }
                        .ifBlank { json.optString("stream", "") }
                        .trim()
                    if (rawPlayback.isBlank()) return null
                    return if (tokenType.equals("sp", ignoreCase = true)) {
                        decodeSpShiftedToken(rawPlayback)
                    } else {
                        decodeWorkerPlaybackUrl(rawPlayback)
                    }
                }

                // 3. Handle `"type": "html"` Clappr array `.join("")` (`Lha/b->i` in sd5yox.apk)
                val clapprJoinedUrl = extractHtmlClapprJoinUrl(bodyStr)
                if (!clapprJoinedUrl.isNullOrBlank()) {
                    return clapprJoinedUrl
                }

                // 4. Handle `"type": "embed"` HTML players (`Lha/b->j` in sd5yox.apk: `var m3u8Url = "..."`)
                val unescapedBody = bodyStr.replace("\\/", "/")
                val extractedStream = Regex("""(?:m3u8Url|videoUrl|streamUrl|hlsUrl|file|source|src)\s*[:=]\s*["'](https?://[^"'\s<>]+)["']""", RegexOption.IGNORE_CASE)
                    .find(unescapedBody)?.groupValues?.getOrNull(1)
                    ?: Regex("""(https?://[^\s"'<>]+\.(?:m3u8|mp4|mpd)[^\s"'<>]*)""", RegexOption.IGNORE_CASE)
                        .find(unescapedBody)?.groupValues?.getOrNull(1)
                if (!extractedStream.isNullOrBlank()) {
                    extractedStream.trim()
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    internal fun decodeWorkerPlaybackUrl(rawPlayback: String): String {
        val trimmed = rawPlayback.trim()
        val urlPart = trimmed.substringBefore("|").trim().removeSuffix("?")
        val pipePart = if (trimmed.contains("|")) trimmed.substringAfter("|").trim() else ""
        val decodedUrl = if (
            urlPart.startsWith("http%3A", ignoreCase = true) ||
            urlPart.startsWith("https%3A", ignoreCase = true)
        ) {
            runCatching { URLDecoder.decode(urlPart, "UTF-8").trim().removeSuffix("?") }.getOrDefault(urlPart)
        } else {
            urlPart
        }
        return if (pipePart.isNotBlank()) "$decodedUrl|$pipePart" else decodedUrl
    }

    private fun isDeadOrPlaceholderStreamUrl(url: String): Boolean {
        val clean = url.substringBefore("|").trim().lowercase(Locale.US)
        if (clean.isBlank()) return true
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return true
        return clean == "http://no.link" ||
            clean == "https://no.link" ||
            clean.startsWith("http://no.link/") ||
            clean.startsWith("https://no.link/") ||
            clean == "http://error" ||
            clean == "https://error" ||
            clean.contains("://error.m3u8") ||
            clean.endsWith(".txt") ||
            clean.contains("workers.dev/?q=")
    }

    private fun isLikelyEmbedOrWorkerPage(url: String): Boolean {
        val clean = url.substringBefore("|").trim().lowercase(Locale.US)
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return false
        if (clean.contains(".m3u8") || clean.contains(".mpd") || clean.contains(".mp4") || clean.contains(".ts")) {
            return false
        }
        return clean.contains("workers.dev") ||
            clean.contains("soccerfull.") ||
            clean.contains("/play/") ||
            clean.contains("/embed/") ||
            clean.contains("player.")
    }

    internal fun parsePlayzStreamItemsJson(
        decryptedJson: String,
        idPrefix: String = "pz"
    ): List<StreamSource> {
        return try {
            val arr = JSONArray(decryptedJson)
            val list = mutableListOf<StreamSource>()
            for (i in 0 until arr.length()) {
                val rawItem = arr.optJSONObject(i) ?: continue
                val itemObj = when {
                    rawItem.has("link") && rawItem.opt("link") is String &&
                        rawItem.optString("link").trim().startsWith("{") -> {
                        JSONObject(rawItem.optString("link"))
                    }
                    rawItem.has("channel") && rawItem.opt("channel") is String &&
                        rawItem.optString("channel").trim().startsWith("{") -> {
                        JSONObject(rawItem.optString("channel"))
                    }
                    else -> rawItem
                }
                if (!itemObj.optBoolean("visible", true)) continue
                val name = itemObj.optString("name", "Server ${i + 1}").trim().ifBlank { "Server ${i + 1}" }
                val rawLinkField = decodeWorkerPlaybackUrl(itemObj.optString("link", "").trim())

                val tokenApiRaw = itemObj.opt("tokenApi")
                val tokenApiObj = when (tokenApiRaw) {
                    is JSONObject -> tokenApiRaw
                    is String -> {
                        val trimmedToken = tokenApiRaw.trim()
                        if (trimmedToken.startsWith("{")) {
                            runCatching { JSONObject(trimmedToken) }.getOrNull()
                        } else {
                            null
                        }
                    }
                    else -> null
                }

                val tokenType = tokenApiObj?.optString("type", "")?.trim().orEmpty()
                val tokenApiEndpoint = tokenApiObj?.optString("api", "")?.trim().orEmpty()
                val tokenLinkKey = tokenApiObj?.optString("link_key", "playback_url")?.trim().ifNullOrBlank { "playback_url" }

                val resolvedFromEndpoint = when {
                    (tokenType.isNotBlank() || rawLinkField.isBlank() || isDeadOrPlaceholderStreamUrl(rawLinkField)) &&
                        tokenApiEndpoint.startsWith("http", ignoreCase = true) -> tokenApiEndpoint
                    isLikelyEmbedOrWorkerPage(rawLinkField) -> rawLinkField
                    else -> ""
                }

                val rawLink = when {
                    (tokenType.isNotBlank() || rawLinkField.isBlank() || isDeadOrPlaceholderStreamUrl(rawLinkField)) &&
                        tokenApiEndpoint.startsWith("http", ignoreCase = true) -> {
                        resolvePlayzWorkerTokenUrl(tokenApiEndpoint, tokenLinkKey, tokenType)
                            ?: if (isLikelyEmbedOrWorkerPage(rawLinkField)) {
                                resolvePlayzWorkerTokenUrl(rawLinkField, tokenLinkKey, tokenType) ?: rawLinkField
                            } else {
                                rawLinkField
                            }
                    }
                    isLikelyEmbedOrWorkerPage(rawLinkField) -> {
                        resolvePlayzWorkerTokenUrl(rawLinkField, tokenLinkKey, tokenType) ?: rawLinkField
                    }
                    else -> rawLinkField
                }.trim()

                val baseLink = rawLink.substringBefore("|").trim().removeSuffix("?")
                if (isDeadOrPlaceholderStreamUrl(baseLink)) {
                    continue
                }
                val embedOrigin = if (resolvedFromEndpoint.isNotBlank()) {
                    Regex("""^(https?://[^/]+)""", RegexOption.IGNORE_CASE)
                        .find(resolvedFromEndpoint.trim())?.groupValues?.getOrNull(1)
                } else {
                    null
                }
                val apiField = itemObj.optString("api", "").trim()
                val isDash = baseLink.contains(".mpd", ignoreCase = true)
                val drmLicense = if (apiField.contains(":") || apiField.startsWith("http", true)) apiField else null
                val itemUserAgent = itemObj.optString("userAgent", "")
                    .ifBlank { itemObj.optString("user_agent", "") }
                    .ifBlank { itemObj.optString("ua", "") }
                    .trim()
                    .ifBlank { null }
                val itemReferer = itemObj.optString("referer", "")
                    .ifBlank { itemObj.optString("referrer", "") }
                    .trim()
                    .ifBlank { embedOrigin?.let { "$it/" }.orEmpty() }
                    .ifBlank { null }
                val itemOrigin = itemObj.optString("origin", "")
                    .trim()
                    .ifBlank { embedOrigin.orEmpty() }
                    .ifBlank { null }
                val itemCookie = itemObj.optString("cookie", "").trim().ifBlank { null }

                val quality = when {
                    name.contains("FHD", true) || name.contains("1080", true) || name.contains("UHD", true) -> "FHD"
                    name.contains("SD", true) || name.contains("LOW", true) || name.contains("480", true) -> "SD"
                    name.contains("HD", true) || name.contains("720", true) -> "HD"
                    isDash -> "FHD"
                    else -> "HD"
                }

                list.add(
                    StreamSource(
                        id = "$idPrefix-s-${i + 1}-${name.hashCode().toUInt()}",
                        name = name,
                        quality = quality,
                        url = rawLink,
                        userAgent = itemUserAgent,
                        referer = itemReferer,
                        origin = itemOrigin,
                        cookie = itemCookie,
                        drmLicense = drmLicense,
                        drmScheme = if (drmLicense != null) "clearkey" else null,
                        isDefault = false,
                        isEnabled = true,
                        priorityOrder = i + 1,
                        isBackup = false
                    )
                )
            }
            // Preserve the exact stream priority order returned by the PlayZ server (`pro/<id>.txt`)
            list.mapIndexed { idx, s ->
                s.copy(isDefault = idx == 0, priorityOrder = idx + 1)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun String?.ifNullOrBlank(defaultValue: () -> String): String =
        if (this.isNullOrBlank()) defaultValue() else this

    /**
     * Fetches `events.txt`, `sports.txt`, and `highlights.txt` from PlayZ Server (`pabbdyzone.shop`)
     * and resolves live matches (Cricket, Football, Boxing, Basketball, Racing, Tennis), channels, and highlights.
     */
    suspend fun fetchPlayzServerCatalog(): PlayzServerCatalog = withContext(Dispatchers.IO) {
        val eventsJob = async { fetchPlayzEndpointDecrypted("events.txt") }
        val highlightsJob = async { fetchPlayzEndpointDecrypted("highlights.txt") }

        val eventsJson = eventsJob.await()
        val highlightsJson = highlightsJob.await()

        val parsedMatches = if (!eventsJson.isNullOrBlank()) {
            parsePlayzEventsJson(eventsJson)
        } else {
            emptyList()
        }

        val parsedHighlights = if (!highlightsJson.isNullOrBlank()) {
            parsePlayzHighlightsJson(highlightsJson)
        } else {
            emptyList()
        }

        // Background-preload only top 4 LIVE matches without blocking the UI catalog response
        val topLiveToWarm = parsedMatches
            .filter { it.status == MatchStatus.LIVE && playzMatchPointerById.containsKey(it.id) }
            .take(4)

        if (topLiveToWarm.isNotEmpty()) {
            scope.launch {
                for (m in topLiveToWarm) {
                    runCatching {
                        val ptr = playzMatchPointerById[m.id] ?: continue
                        val streams = resolvePlayzStreamsForPointer(ptr, m.id)
                        if (streams.isNotEmpty()) {
                            playzMatchesById[m.id] = m.copy(
                                streams = streams,
                                broadcastChannels = streams.map { it.name }.take(4)
                            )
                        }
                    }
                }
            }
        }

        PlayzServerCatalog(
            matches = parsedMatches,
            highlights = parsedHighlights
        )
    }

    internal fun parsePlayzEventsJson(decryptedEventsJson: String): List<LiveMatch> {
        return try {
            val arr = JSONArray(decryptedEventsJson)
            val result = mutableListOf<LiveMatch>()
            val seenEventKeys = HashSet<String>()
            playzMatchesById.clear()
            playzMatchPointerById.clear()
            val nowMs = System.currentTimeMillis()
            val utcFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val utcDateOnlyFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val dhakaFmt = SimpleDateFormat("hh:mm a | dd/MM/yyyy", Locale.US).apply {
                timeZone = DHAKA_TZ
            }
            val todayUtcDateStr = utcDateOnlyFmt.format(Date(nowMs))

            for (i in 0 until arr.length()) {
                val wrapper = arr.optJSONObject(i) ?: continue
                val innerStr = wrapper.optString("event", "")
                val ev = if (innerStr.isNotBlank()) JSONObject(innerStr) else wrapper
                if (!ev.optBoolean("visible", true)) continue

                val categoryRaw = ev.optString("category", "Football").trim()
                val eventName = ev.optString("eventName", categoryRaw).trim()
                var teamA = ev.optString("teamAName", "").trim()
                var teamB = ev.optString("teamBName", "").trim()
                if (teamA.isBlank() && teamB.isBlank()) {
                    if (eventName.contains(" vs ", ignoreCase = true)) {
                        val parts = eventName.split(Regex("\\s+vs\\s+", RegexOption.IGNORE_CASE), limit = 2)
                        teamA = parts.getOrNull(0)?.trim().orEmpty()
                        teamB = parts.getOrNull(1)?.trim().orEmpty()
                    }
                }
                if (teamA.isBlank() && teamB.isBlank()) continue

                val eventLogo = if (ev.isNull("eventLogo")) "" else ev.optString("eventLogo", "").trim()
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .replace("http://", "https://")

                val rawTeamAFlag = (if (ev.isNull("teamAFlag")) "" else ev.optString("teamAFlag", "").trim())
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .let { raw ->
                        when {
                            raw.isBlank() -> eventLogo
                            raw.startsWith("http://", true) -> "https://" + raw.substring(7)
                            !raw.startsWith("https://", true) && !raw.startsWith("//") ->
                                "https://pabbdyzone.shop/${raw.removePrefix("/")}"
                            else -> raw
                        }
                    }

                val rawTeamBFlag = (if (ev.isNull("teamBFlag")) "" else ev.optString("teamBFlag", "").trim())
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .let { raw ->
                        when {
                            raw.isBlank() -> eventLogo
                            raw.startsWith("http://", true) -> "https://" + raw.substring(7)
                            !raw.startsWith("https://", true) && !raw.startsWith("//") ->
                                "https://pabbdyzone.shop/${raw.removePrefix("/")}"
                            else -> raw
                        }
                    }

                val dateStr = if (ev.isNull("date")) "" else ev.optString("date", "").trim().takeUnless { it.equals("null", true) }.orEmpty()
                val timeStr = if (ev.isNull("time")) "00:00:00" else ev.optString("time", "00:00:00").trim().takeUnless { it.equals("null", true) } ?: "00:00:00"
                val endDateStr = if (ev.isNull("end_date")) "" else ev.optString("end_date", "").trim().takeUnless { it.equals("null", true) }.orEmpty()
                val endTimeStr = if (ev.isNull("end_time")) "" else ev.optString("end_time", "").trim().takeUnless { it.equals("null", true) }.orEmpty()
                val linksPointer = if (ev.isNull("links")) "" else ev.optString("links", "").trim().takeUnless { it.equals("null", true) }.orEmpty()

                val linkNamesArr = ev.optJSONArray("link_names")
                val linkNames = buildList {
                    if (linkNamesArr != null) {
                        for (j in 0 until linkNamesArr.length()) {
                            val ln = linkNamesArr.optString(j).trim()
                            if (ln.isNotBlank() && !ln.equals("null", true)) add(ln)
                        }
                    }
                }

                // Skip placeholder/empty non-match entries (e.g. "LaLiga links", "Coppa ITALIA Links", "NPC Links", "NRL LINKS")
                if (teamA.endsWith("links", ignoreCase = true) ||
                    teamB.endsWith("links", ignoreCase = true) ||
                    (linkNames.isEmpty() && linksPointer.isBlank())
                ) {
                    continue
                }

                val sportType = mapCategoryToSportType(categoryRaw, eventName, teamA, teamB)
                val isNatMatch = com.example.data.util.TeamFlagResolver.isRecognizedNationalTeam(teamA) ||
                    com.example.data.util.TeamFlagResolver.isRecognizedNationalTeam(teamB)
                val resolvedTeamA = com.example.data.util.TeamFlagResolver.resolveForMatch(
                    teamName = teamA,
                    existingFlag = rawTeamAFlag,
                    isNationalTeam = isNatMatch
                )
                val resolvedTeamB = com.example.data.util.TeamFlagResolver.resolveForMatch(
                    teamName = teamB,
                    existingFlag = rawTeamBFlag,
                    isNationalTeam = isNatMatch
                )
                val teamAFlag = rawTeamAFlag.ifBlank { resolvedTeamA.flagUrl }
                val teamBFlag = rawTeamBFlag.ifBlank { resolvedTeamB.flagUrl }

                val startEpochMs = runCatching {
                    if (dateStr.isNotBlank()) utcFmt.parse("$dateStr $timeStr")?.time ?: 0L else 0L
                }.getOrDefault(0L)

                val todayProjectedStartMs = runCatching {
                    utcFmt.parse("$todayUtcDateStr $timeStr")?.time ?: 0L
                }.getOrDefault(0L)

                val isShortFormatCricket = eventName.contains("T10", true) ||
                    eventName.contains("T20", true) ||
                    eventName.contains("IPL", true) ||
                    eventName.contains("PSL", true) ||
                    eventName.contains("BPL", true) ||
                    eventName.contains("Big Bash", true) ||
                    eventName.contains("BBL", true) ||
                    eventName.contains("CPL", true) ||
                    eventName.contains("LPL", true) ||
                    eventName.contains("SA20", true) ||
                    eventName.contains("ILT20", true) ||
                    eventName.contains("Hundred", true) ||
                    eventName.contains("Blast", true)

                val defaultDurationMs = when {
                    sportType == SportType.CRICKET && eventName.contains("T10", true) ->
                        35 * 360_000L // 3.5 hours
                    sportType == SportType.CRICKET && isShortFormatCricket ->
                        6 * 3600_000L // 6 hours for T20
                    sportType == SportType.CRICKET ->
                        10 * 3600_000L // 10 hours for ODI / One-Day / International Series / Test day (e.g. Pakistan vs UAE)
                    else ->
                        4 * 3600_000L // 4 hours for Football & other sports
                }

                val parsedEndMs = runCatching {
                    if (endDateStr.isNotBlank() && endTimeStr.isNotBlank()) {
                        utcFmt.parse("$endDateStr $endTimeStr")?.time ?: 0L
                    } else {
                        0L
                    }
                }.getOrDefault(0L)

                // Ensure server's short placeholder end_time never cuts off an active Cricket/Football match early
                val endEpochMs = when {
                    startEpochMs > 0L && parsedEndMs > 0L -> maxOf(parsedEndMs, startEpochMs + defaultDurationMs)
                    parsedEndMs > 0L -> parsedEndMs
                    startEpochMs > 0L -> startEpochMs + defaultDurationMs
                    else -> 0L
                }

                // Mark LIVE starting 30 minutes before kickoff (toss / pre-match broadcast) through full match duration.
                // Also support multi-day/reused series cards in events.txt whose daily time window is currently active.
                val liveStartThresholdMs = if (startEpochMs > 30 * 60_000L) startEpochMs - 30 * 60_000L else startEpochMs
                val todayWindowStartMs = if (todayProjectedStartMs > 30 * 60_000L) todayProjectedStartMs - 30 * 60_000L else todayProjectedStartMs
                val todayWindowEndMs = if (todayProjectedStartMs > 0L) todayProjectedStartMs + defaultDurationMs else 0L
                val isDailySeriesWindowLive = sportType == SportType.CRICKET &&
                    startEpochMs in 1..nowMs &&
                    todayProjectedStartMs > 0L &&
                    nowMs in todayWindowStartMs..todayWindowEndMs

                val status = when {
                    (startEpochMs > 0L && nowMs in liveStartThresholdMs..endEpochMs) || isDailySeriesWindowLive ->
                        MatchStatus.LIVE
                    startEpochMs > nowMs ->
                        MatchStatus.UPCOMING
                    endEpochMs in 1 until nowMs ->
                        MatchStatus.FINISHED
                    else ->
                        MatchStatus.LIVE
                }

                // Deduplicate identical events in events.txt (including duplicate fixtures with different pointer timestamps)
                val dedupKey = "${sportType.name}|${normalizeTeamToken(teamA)}|${normalizeTeamToken(teamB)}|$dateStr|$timeStr"
                if (!seenEventKeys.add(dedupKey)) {
                    continue
                }

                val matchId = "pz-${(eventName + "_" + teamA + "_" + teamB + "_" + dateStr + "_" + timeStr + "_" + linksPointer).lowercase(Locale.US).hashCode().toUInt()}"
                if (linksPointer.isNotBlank()) {
                    playzMatchPointerById[matchId] = linksPointer
                }

                // Never attach fake placeholder buttons or .txt URLs; only attach already-decrypted streams for this exact pointer
                val cleanPointerKey = linksPointer.removePrefix("/")
                val cachedResolved = if (cleanPointerKey.isNotBlank()) {
                    playzResolvedStreamsByPointer[cleanPointerKey]
                } else {
                    null
                }
                val initialStreams = cachedResolved ?: emptyList()

                val effectiveDisplayEpoch = if (isDailySeriesWindowLive && todayProjectedStartMs > 0L) {
                    todayProjectedStartMs
                } else {
                    startEpochMs
                }
                val formattedDhakaTime = if (effectiveDisplayEpoch > 0L) {
                    dhakaFmt.format(Date(effectiveDisplayEpoch))
                } else {
                    "$timeStr | $dateStr"
                }

                val match = LiveMatch(
                    id = matchId,
                    tournament = "${categoryRaw.uppercase()} | ${eventName.uppercase()}",
                    sport = sportType,
                    team1Name = teamA,
                    team1Code = resolvedTeamA.code,
                    team1FlagUrl = teamAFlag,
                    team2Name = teamB,
                    team2Code = resolvedTeamB.code,
                    team2FlagUrl = teamBFlag,
                    status = status,
                    timeOrScore = when (status) {
                        MatchStatus.LIVE -> "LIVE NOW"
                        MatchStatus.FINISHED -> "Finished"
                        else -> "Upcoming"
                    },
                    matchTime = formattedDhakaTime,
                    streams = initialStreams,
                    provider = "PLAYZ",
                    competitionId = eventName.lowercase(Locale.US),
                    startTimeEpochMs = effectiveDisplayEpoch,
                    isNationalTeam = isNatMatch,
                    broadcastChannels = if (initialStreams.isNotEmpty()) {
                        initialStreams.map { it.name }.take(4)
                    } else {
                        linkNames.take(4)
                    }
                )
                playzMatchesById[matchId] = match
                result.add(match)
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePlayzHighlightsJson(decryptedHighlightsJson: String): List<HighlightItem> {
        return try {
            val arr = JSONArray(decryptedHighlightsJson)
            val indexedItems = mutableListOf<Pair<Int, HighlightItem>>()
            val utcFmt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val dhakaTimeFmt = SimpleDateFormat("hh:mm a", Locale.US).apply {
                timeZone = DHAKA_TZ
            }
            val dhakaDateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
                timeZone = DHAKA_TZ
            }

            for (i in 0 until arr.length()) {
                val wrapper = arr.optJSONObject(i) ?: continue
                val innerStr = wrapper.optString("highlight", "")
                val hl = if (innerStr.isNotBlank()) JSONObject(innerStr) else wrapper
                if (!hl.optBoolean("visible", true)) continue
                val cat = hl.optString("category", "Football").trim().ifBlank { "Football" }
                val eventName = hl.optString("eventName", cat).trim().ifBlank { cat }
                val teamA = hl.optString("teamAName", "").trim()
                val teamB = hl.optString("teamBName", "").trim()
                if (teamA.isBlank() && teamB.isBlank()) continue

                val goalsA = hl.optString("teamAGoals", "")
                val goalsB = hl.optString("teamBGoals", "")
                val logo = hl.optString("eventLogo", "").trim()
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .replace("http://", "https://")
                val teamALogo = hl.optString("teamAFlag", "").trim()
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .replace("http://", "https://")
                    .ifBlank { logo }
                val teamBLogo = hl.optString("teamBFlag", "").trim()
                    .takeUnless { it.equals("null", true) }.orEmpty()
                    .replace("http://", "https://")
                    .ifBlank { logo }

                val rawDate = hl.optString("date", "").trim()
                val rawTime = hl.optString("time", "00:00:00").trim().ifBlank { "00:00:00" }
                val epochMs = if (rawDate.isNotBlank()) {
                    runCatching { utcFmt.parse("$rawDate $rawTime")?.time ?: 0L }.getOrDefault(0L)
                } else {
                    0L
                }
                val timeFormatted = if (epochMs > 0L) dhakaTimeFmt.format(Date(epochMs)) else rawTime.take(5)
                val dateFormatted = if (epochMs > 0L) dhakaDateFmt.format(Date(epochMs)) else rawDate

                val linksPtr = hl.optString("links", "").trim()
                val title = if (goalsA.isNotBlank() && goalsB.isNotBlank()) {
                    "$teamA $goalsA - $goalsB $teamB"
                } else {
                    "$teamA vs $teamB"
                }
                indexedItems.add(
                    i to HighlightItem(
                        id = "pz-hl-$i",
                        title = title,
                        tournament = eventName,
                        categoryLabel = cat,
                        eventLogo = logo,
                        team1Name = teamA,
                        team2Name = teamB,
                        team1Logo = teamALogo,
                        team2Logo = teamBLogo,
                        timeText = timeFormatted,
                        dateText = dateFormatted,
                        timestampEpochMs = epochMs,
                        duration = if (dateFormatted.isNotBlank()) "$timeFormatted $dateFormatted" else "Highlights",
                        thumbnailUrl = logo,
                        videoUrl = if (linksPtr.startsWith("http")) linksPtr else "https://pabbdyzone.shop/${linksPtr.removePrefix("/")}",
                        sport = mapCategoryToSportType(cat, eventName, teamA, teamB),
                        isApproved = true
                    )
                )
            }
            indexedItems
                .sortedWith(
                    compareByDescending<Pair<Int, HighlightItem>> { it.second.timestampEpochMs }
                        .thenBy { it.first }
                )
                .map { it.second }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapCategoryToSportType(
        categoryRaw: String,
        eventName: String,
        teamA: String = "",
        teamB: String = ""
    ): SportType {
        val combined = "$categoryRaw $eventName".lowercase(Locale.US)
        val teamsLower = "$teamA $teamB".lowercase(Locale.US)
        val cricketNationHints = listOf(
            "pakistan", "india", "bangladesh", "sri lanka", "afghanistan", "zimbabwe",
            "west indies", "windies", "united arab emirates", "uae", "oman", "nepal",
            "namibia", "scotland", "ireland", "papua new guinea", "hong kong"
        )
        val hasExplicitFootball = combined.contains("football") || combined.contains("soccer") ||
            combined.contains("uefa") || combined.contains("laliga") || combined.contains("premier league") ||
            combined.contains("serie a") || combined.contains("bundesliga") || combined.contains("champions league") ||
            combined.contains("europa") || combined.contains("copa") || combined.contains("ligue 1") ||
            combined.contains("world cup qualification") || combined.contains("afc Asian")
        return when {
            combined.contains("cricket") || combined.contains("ipl") || combined.contains("psl") ||
                combined.contains("bpl") || combined.contains("big bash") || combined.contains("bbl") ||
                combined.contains("wtc") || combined.contains("odi") || combined.contains("t20") ||
                combined.contains("t10") || combined.contains("test series") || combined.contains("icc") ||
                combined.contains("acc ") || combined.contains("asia cup") || combined.contains("tri-series") ||
                combined.contains("tri series") || combined.contains("tour of") || combined.contains("champions trophy") ||
                combined.contains("ilt20") || combined.contains("sa20") || combined.contains("cpl") ||
                combined.contains("lpl") || combined.contains("hundred") || combined.contains("super smash") ||
                (!hasExplicitFootball && cricketNationHints.count { teamsLower.contains(it) } >= 2) -> SportType.CRICKET
            hasExplicitFootball || combined.contains("golazo") -> SportType.FOOTBALL
            combined.contains("boxing") || combined.contains("mma") || combined.contains("ufc") ||
                combined.contains("wwe") || combined.contains("aew") || combined.contains("fight") ||
                combined.contains("pfl") || combined.contains("smackdown") || combined.contains("raw") ||
                combined.contains("martial") -> SportType.BOXING
            combined.contains("basketball") || combined.contains("nba") || combined.contains("euroleague") -> SportType.BASKETBALL
            combined.contains("motorsport") || combined.contains("formula 1") || combined.contains("f1") ||
                combined.contains("motogp") || combined.contains("racing") || combined.contains("nascar") -> SportType.RACING
            combined.contains("tennis") || combined.contains("atp") || combined.contains("wta") ||
                combined.contains("wimbledon") || combined.contains("snooker") -> SportType.TENNIS
            combined.contains("hockey") || combined.contains("nhl") -> SportType.HOCKEY
            else -> SportType.FOOTBALL
        }
    }

    fun getPlayzPointerForMatch(match: LiveMatch): String? {
        if (match.provider == "PLAYZ") {
            return playzMatchPointerById[match.id]
        }
        playzMatchPointerById[match.id]?.let { return it }
        val found = findMatchingPlayzEvent(match)
        return found?.let { playzMatchPointerById[it.id] }
    }

    private fun findMatchingPlayzEvent(match: LiveMatch): LiveMatch? {
        playzMatchesById[match.id]?.let { return it }
        val t1 = normalizeTeamToken(match.team1Name)
        val t2 = normalizeTeamToken(match.team2Name)
        if (t1.length < 2 || t2.length < 2) return null

        return playzMatchesById.values.firstOrNull { candidate ->
            candidate.sport == match.sport &&
                run {
                    val c1 = normalizeTeamToken(candidate.team1Name)
                    val c2 = normalizeTeamToken(candidate.team2Name)
                    val directMatch = c1 == t1 && c2 == t2
                    val swappedMatch = c1 == t2 && c2 == t1
                    directMatch || swappedMatch
                }
        }
    }

    private fun normalizeTeamToken(name: String): String {
        val cleaned = name.lowercase(Locale.US)
            .replace("united arab emirates", "uae")
            .replace("united states of america", "usa")
            .replace("united states", "usa")
            .replace("south africa", "rsa")
            .replace("new zealand", "nz")
            .replace("sri lanka", "sl")
            .replace("west indies", "wi")
            .replace("windies", "wi")
            .replace(Regex("\\b(fc|cf|sc|ac|afc|club|women|w|u19|under 19|u-19|a|shaheens|emerging|jk|sk|fk|nk)\\b"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned
    }

    fun registerAdminCustomM3uEntries(
        entries: List<PlaylistEntry>,
        healthByUrl: Map<String, Boolean> = emptyMap()
    ) {
        for ((url, isAlive) in healthByUrl) {
            streamHealthMap[url] = isAlive
        }
        for (entry in entries) {
            adminCustomEntries[entry.cleanName] = entry
            dynamicEntriesByKey[entry.cleanName] = entry
        }
        totalDiscoveredStreams = dynamicEntriesByKey.size
    }

    fun registerAdminM3uBlock(rawM3uBlock: String) {
        if (rawM3uBlock.isBlank()) return
        val parsed = parseFlexibleAdminM3uText(rawM3uBlock)
        if (parsed.isNotEmpty()) {
            registerAdminCustomM3uEntries(parsed)
        }
    }

    fun parseFlexibleAdminM3uText(rawText: String): List<PlaylistEntry> {
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return emptyList()

        val results = mutableListOf<PlaylistEntry>()
        var pendingName = ""
        var pendingLogo = ""
        var pendingGroup = ""
        var pendingReferer: String? = null
        var pendingOrigin: String? = null
        var pendingUserAgent: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    // Header
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(":").trim()
                    val k = opt.substringBefore("=").trim().lowercase(Locale.US)
                    val v = opt.substringAfter("=", "").trim()
                    if (v.isNotBlank()) {
                        when (k) {
                            "http-referrer", "http-referer", "referer" -> pendingReferer = v
                            "http-origin", "origin" -> pendingOrigin = v
                            "http-user-agent", "user-agent" -> pendingUserAgent = v
                        }
                    }
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingName = line.substringAfterLast(",", "").trim()
                    pendingLogo = Regex("""tvg-logo="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                    pendingGroup = Regex("""group-title="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                }
                line.contains(": http://", ignoreCase = true) || line.contains(": https://", ignoreCase = true) -> {
                    val namePart = line.substringBefore(": http").trim()
                    val fullUrlPart = ("http" + line.substringAfter(": http")).trim()
                    if (fullUrlPart.startsWith("http")) {
                        val lower = namePart.lowercase(Locale.US)
                        val cat = inferSportCategory(lower)
                        val clean = normalizeChannelName(namePart).ifBlank { "ch_${results.size + 1}" }
                        val finalUrl = appendHeadersToPipeUrl(fullUrlPart, pendingReferer, pendingOrigin, pendingUserAgent)
                        results.add(
                            PlaylistEntry(
                                id = "adm-${clean.hashCode().toUInt()}",
                                name = namePart.ifBlank { "Sports Live ${results.size + 1}" },
                                cleanName = clean,
                                url = finalUrl,
                                quality = if (fullUrlPart.contains("1080") || namePart.contains("FHD", true)) "1080p" else if (fullUrlPart.contains("720")) "720p" else "1080p",
                                logoUrl = "",
                                category = cat,
                                userAgent = pendingUserAgent,
                                referer = pendingReferer,
                                origin = pendingOrigin
                            )
                        )
                    }
                    pendingName = ""
                    pendingLogo = ""
                    pendingGroup = ""
                    pendingReferer = null
                    pendingOrigin = null
                    pendingUserAgent = null
                }
                line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true) -> {
                    val fullUrl = line.trim()
                    val baseOnly = fullUrl.substringBefore("|").trim()
                    val resolvedName = pendingName.ifBlank {
                        baseOnly.substringBeforeLast("/")
                            .substringAfterLast("/")
                            .replace("_", " ")
                            .replace("-", " ")
                            .trim()
                            .takeIf { it.isNotBlank() && !it.equals("live", true) && !it.equals("hls", true) }
                            ?: "Sports Stream ${results.size + 1}"
                    }
                    val lower = "$resolvedName $pendingGroup".lowercase(Locale.US)
                    val cat = inferSportCategory(lower)
                    val clean = normalizeChannelName(resolvedName).ifBlank { "ch_${results.size + 1}" }
                    val finalUrl = appendHeadersToPipeUrl(fullUrl, pendingReferer, pendingOrigin, pendingUserAgent)
                    results.add(
                        PlaylistEntry(
                            id = "adm-${clean.hashCode().toUInt()}",
                            name = resolvedName,
                            cleanName = clean,
                            url = finalUrl,
                            quality = if (baseOnly.contains("1080") || resolvedName.contains("1080", true)) "1080p" else if (baseOnly.contains("720")) "720p" else "1080p",
                            logoUrl = pendingLogo,
                            category = cat,
                            userAgent = pendingUserAgent,
                            referer = pendingReferer,
                            origin = pendingOrigin
                        )
                    )
                    pendingName = ""
                    pendingLogo = ""
                    pendingGroup = ""
                    pendingReferer = null
                    pendingOrigin = null
                    pendingUserAgent = null
                }
            }
        }
        return results.distinctBy { it.url }
    }

    private fun appendHeadersToPipeUrl(
        rawUrl: String,
        referer: String?,
        origin: String?,
        userAgent: String?
    ): String {
        if (rawUrl.contains("|")) return rawUrl
        val params = mutableListOf<String>()
        if (!referer.isNullOrBlank()) params.add("referer=$referer")
        if (!origin.isNullOrBlank()) params.add("origin=$origin")
        if (!userAgent.isNullOrBlank()) params.add("user-agent=$userAgent")
        return if (params.isEmpty()) rawUrl else "$rawUrl|${params.joinToString("&")}"
    }

    fun describeAutoMatchTarget(name: String, category: String): String {
        val lower = name.lowercase(Locale.US)
        return when {
            lower.contains("t sport") || lower.contains("tsport") || lower.contains("gtv") || lower.contains("gazi") ->
                "🏏 Cricket & ⚽ Football • Bangladesh / All Live Matches"
            lower.contains("a sport") || lower.contains("ptv") || lower.contains("ten cricket") ->
                "🏏 Cricket • Pakistan / ICC / Asia Cup Matches"
            lower.contains("willow") || lower.contains("cricket gold") || lower.contains("dd sport") ->
                "🏏 Cricket • International / ICC / Test / ODI / T20"
            lower.contains("star sport") ->
                "🏏 Cricket (IND/ICC) & ⚽ Football (Premier League)"
            lower.contains("sony") || lower.contains("ten 1") || lower.contains("ten 2") || lower.contains("ten 5") ->
                "⚽ Football (UCL / Europe) & 🏏 Cricket"
            lower.contains("tyc") ->
                "⚽ Football • LIGA PROFESIONAL DE FÚTBOL (Argentina)"
            lower.contains("telemundo") ->
                "⚽ Football • LIGA MX, APERTURA & CONMEBOL"
            lower.contains("bein") || lower.contains("real madrid") || lower.contains("rmtv") ->
                "⚽ Football • LALIGA, LALIGA 2, LIGA PORTUGAL & UCL"
            lower.contains("tsn") || lower.contains("fox") || lower.contains("fubo") || lower.contains("nbc") ->
                "⚽ Football • EREDIVISIE, LIGA PORTUGAL, LALIGA & FIFA"
            lower.contains("espn") || lower.contains("cbs") || lower.contains("golazo") ->
                "⚽ Football • LALIGA, Serie A, UCL & International"
            category.equals("Cricket", ignoreCase = true) || category.equals("Bangla Sports", ignoreCase = true) || category.equals("Pakistan", ignoreCase = true) ->
                "🏏 Cricket • Auto-Mapped to All Live Cricket Matches"
            else ->
                "⚽ Football & 🏏 Cricket • Auto-Mapped to Live Matches"
        }
    }

    fun getDiagnostics(): ProviderSyncDiagnostics {
        val syncEpoch = lastPlaylistSyncEpochMs.takeIf { it > 0L }
        val dhakaFmt = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'BST'", Locale.US).apply {
            timeZone = DHAKA_TZ
        }
        val activeVerifiedCount = streamHealthMap.values.count { it }
        return ProviderSyncDiagnostics(
            providerName = "PlayZ Server & Live M3U Stream Resolver",
            enabled = true,
            lastSyncEpochMs = syncEpoch,
            lastSyncDhakaText = syncEpoch?.let { dhakaFmt.format(Date(it)) } ?: "Seed Ready (${VERIFIED_LIVE_SEED_ENTRIES.size} live streams)",
            statusText = if (syncEpoch != null) {
                "Synced ($totalDiscoveredStreams streams, ${playzMatchesById.size} PlayZ events, $activeVerifiedCount verified)"
            } else {
                "Active (${VERIFIED_LIVE_SEED_ENTRIES.size} verified streams)"
            },
            lastError = lastPlaylistError,
            activeMatchCount = totalDiscoveredStreams,
            isStale = false
        )
    }

    suspend fun refreshLivePlaylists(
        force: Boolean = false,
        extraM3uUrls: List<String> = emptyList()
    ): List<PlaylistEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && lastPlaylistSyncEpochMs > 0L && (now - lastPlaylistSyncEpochMs) < PLAYLIST_CACHE_TTL_MS) {
            return@withContext getAllActivePlaylistEntries()
        }

        syncMutex.withLock {
            val recheckNow = System.currentTimeMillis()
            if (!force && lastPlaylistSyncEpochMs > 0L && (recheckNow - lastPlaylistSyncEpochMs) < PLAYLIST_CACHE_TTL_MS) {
                return@withLock getAllActivePlaylistEntries()
            }

            val urlsToFetch = (DEFAULT_PLAYLIST_URLS + extraM3uUrls.filter { it.endsWith(".m3u", true) || it.endsWith(".m3u8", true) })
                .distinct()

            val jobs = urlsToFetch.map { url ->
                scope.async {
                    fetchAndParseM3uUrl(url)
                }
            }

            val results = jobs.awaitAll()
            var anySuccess = false
            var firstErr: String? = null

            for (res in results) {
                res.fold(
                    onSuccess = { parsedList ->
                        if (parsedList.isNotEmpty()) {
                            anySuccess = true
                            mergeParsedPlaylistEntries(parsedList)
                        }
                    },
                    onFailure = { err ->
                        if (firstErr == null) {
                            firstErr = err.localizedMessage
                        }
                    }
                )
            }

            for (seed in VERIFIED_LIVE_SEED_ENTRIES) {
                dynamicEntriesByKey.putIfAbsent(seed.cleanName, seed)
            }

            probeSeedStreamsConcurrently()

            totalDiscoveredStreams = dynamicEntriesByKey.size
            if (anySuccess || streamHealthMap.isNotEmpty()) {
                lastPlaylistSyncEpochMs = System.currentTimeMillis()
                lastPlaylistError = null
            } else if (firstErr != null) {
                lastPlaylistError = firstErr
            }

            getAllActivePlaylistEntries()
        }
    }

    private fun probeSeedStreamsConcurrently() {
        scope.launch {
            for (batch in VERIFIED_LIVE_SEED_ENTRIES.chunked(6)) {
                val probeJobs = batch.map { entry ->
                    async {
                        val cached = streamHealthMap[entry.url]
                        if (cached != null) return@async
                        val isOnline = checkStreamUrlAlive(entry.url)
                        streamHealthMap[entry.url] = isOnline
                    }
                }
                runCatching { probeJobs.awaitAll() }
            }
        }
    }

    private fun checkStreamUrlAlive(url: String): Boolean {
        return try {
            val cleanUrl = url.substringBefore("|").trim()
            val req = Request.Builder()
                .url(cleanUrl)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "*/*")
                .build()
            probeClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return false
                val peek = resp.peekBody(256).string()
                peek.contains("#EXTM3U", ignoreCase = true) ||
                    peek.contains("<MPD", ignoreCase = true) ||
                    resp.header("Content-Type").orEmpty().contains("mpegurl", ignoreCase = true) ||
                    cleanUrl.contains(".m3u8", ignoreCase = true) ||
                    cleanUrl.contains(".mpd", ignoreCase = true)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getAllActivePlaylistEntries(): List<PlaylistEntry> {
        return dynamicEntriesByKey.values
            .filter { streamHealthMap[it.url] != false }
            .ifEmpty { dynamicEntriesByKey.values.toList() }
    }

    private fun fetchAndParseM3uUrl(m3uUrl: String): Result<List<PlaylistEntry>> {
        return try {
            val req = Request.Builder()
                .url(m3uUrl)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Android; SportsLiveTV)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("HTTP ${resp.code} from $m3uUrl")
                }
                val content = resp.body?.string().orEmpty()
                Result.success(parseSportsM3uContent(content))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parses `#EXTM3U` content, preserving `#EXTVLCOPT` (`http-referrer`, `http-origin`, `http-user-agent`)
     * and `|` pipe parameters, and indexing both live channels and `Team A vs Team B` match streams.
     */
    internal fun parseSportsM3uContent(rawM3u: String): List<PlaylistEntry> {
        val lines = rawM3u.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.isEmpty()) return emptyList()

        val sportsFilterKeywords = listOf(
            "sport", "cricket", "football", "soccer", "sony", "star sports", "t sports", "tsports",
            "willow", "ptv", "a sports", "ten ", "bein", "espn", "cbs", "golazo", "dazn", "fifa",
            "sportitalia", "real madrid", "barca", "inter tv", "milan tv", "eurosport",
            "premier sports", "sky sports", "red bull", "fight", "oman sports", "dubai sports",
            "sharjah sports", "dd sports", "telemundo", "tyc", "tsn", "fox", "fubo", "nbc", "a spor",
            " vs ", " v ", "liga", "uefa", "nba", "wta", "atp", "f1", "ufc", "boxing"
        )

        val excludedNonSportsKeywords = listOf(
            "azan tv", "bloomberg", "fm radio", "cowboy", "horse"
        )

        val parsed = mutableListOf<PlaylistEntry>()
        var idx = 0
        var pendingReferer: String? = null
        var pendingOrigin: String? = null
        var pendingUserAgent: String? = null

        while (idx < lines.size) {
            val line = lines[idx]
            if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = line.substringAfter(":").trim()
                val k = opt.substringBefore("=").trim().lowercase(Locale.US)
                val v = opt.substringAfter("=", "").trim()
                if (v.isNotBlank()) {
                    when (k) {
                        "http-referrer", "http-referer", "referer" -> pendingReferer = v
                        "http-origin", "origin" -> pendingOrigin = v
                        "http-user-agent", "user-agent" -> pendingUserAgent = v
                    }
                }
                idx++
                continue
            }

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                val rawName = line.substringAfterLast(",", "").trim()
                val tvgName = Regex("""tvg-name="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                val logoUrl = Regex("""tvg-logo="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                val groupTitle = Regex("""group-title="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                val effectiveTitle = rawName.ifBlank { tvgName }

                idx++
                while (idx < lines.size && lines[idx].startsWith("#")) {
                    val metaLine = lines[idx]
                    if (metaLine.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                        val opt = metaLine.substringAfter(":").trim()
                        val k = opt.substringBefore("=").trim().lowercase(Locale.US)
                        val v = opt.substringAfter("=", "").trim()
                        if (v.isNotBlank()) {
                            when (k) {
                                "http-referrer", "http-referer", "referer" -> pendingReferer = v
                                "http-origin", "origin" -> pendingOrigin = v
                                "http-user-agent", "user-agent" -> pendingUserAgent = v
                            }
                        }
                    }
                    idx++
                }

                if (idx < lines.size) {
                    val rawStreamUrl = lines[idx]
                    // Also check trailing #EXTVLCOPT lines immediately following the URL (as in BDxTV playlist_s.m3u)
                    var lookAhead = idx + 1
                    while (lookAhead < lines.size && lines[lookAhead].startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                        val opt = lines[lookAhead].substringAfter(":").trim()
                        val k = opt.substringBefore("=").trim().lowercase(Locale.US)
                        val v = opt.substringAfter("=", "").trim()
                        if (v.isNotBlank()) {
                            when (k) {
                                "http-referrer", "http-referer", "referer" -> pendingReferer = v
                                "http-origin", "origin" -> pendingOrigin = v
                                "http-user-agent", "user-agent" -> pendingUserAgent = v
                            }
                        }
                        lookAhead++
                    }

                    if ((rawStreamUrl.startsWith("http://", true) || rawStreamUrl.startsWith("https://", true)) &&
                        (rawStreamUrl.contains(".m3u8", ignoreCase = true) || rawStreamUrl.contains(".mpd", ignoreCase = true)) &&
                        !line.contains("[Geo-blocked]", ignoreCase = true)
                    ) {
                        val lowerName = effectiveTitle.lowercase(Locale.US)
                        val lowerGroup = groupTitle.lowercase(Locale.US)
                        val isExcluded = excludedNonSportsKeywords.any { lowerName.contains(it) }
                        val isSportsChannel = !isExcluded && (
                            lowerGroup.contains("sport") ||
                                lowerGroup.contains("live") ||
                                sportsFilterKeywords.any { lowerName.contains(it) }
                            )

                        if (isSportsChannel && effectiveTitle.isNotBlank()) {
                            val quality = when {
                                effectiveTitle.contains("1080p", true) || effectiveTitle.contains("FHD", true) -> "1080p"
                                effectiveTitle.contains("720p", true) || effectiveTitle.contains("HD", true) -> "720p"
                                else -> "HD"
                            }
                            val displayTitle = effectiveTitle
                                .replace(Regex("\\s*\\[[^]]*]"), "")
                                .replace(Regex("\\s*\\(\\d+p\\)", RegexOption.IGNORE_CASE), "")
                                .trim()
                            val cleanKey = normalizeChannelName(displayTitle)
                            val cat = inferSportCategory("$lowerName $lowerGroup")
                            val fullPipeUrl = appendHeadersToPipeUrl(
                                rawStreamUrl,
                                pendingReferer,
                                pendingOrigin,
                                pendingUserAgent
                            )

                            if (cleanKey.isNotBlank()) {
                                val entry = PlaylistEntry(
                                    id = "m3u-${(cleanKey + fullPipeUrl).hashCode().toUInt()}",
                                    name = displayTitle,
                                    cleanName = cleanKey,
                                    url = fullPipeUrl,
                                    quality = quality,
                                    logoUrl = logoUrl,
                                    category = cat,
                                    userAgent = pendingUserAgent,
                                    referer = pendingReferer,
                                    origin = pendingOrigin
                                )
                                parsed.add(entry)
                                if (displayTitle.contains(" vs ", ignoreCase = true)) {
                                    m3uEventEntries[entry.id] = entry
                                }
                            }
                        }
                    }
                    pendingReferer = null
                    pendingOrigin = null
                    pendingUserAgent = null
                    idx = lookAhead
                    continue
                }
            }
            idx++
        }
        return parsed
    }

    private fun mergeParsedPlaylistEntries(entries: List<PlaylistEntry>) {
        val seedCleanNames = VERIFIED_LIVE_SEED_ENTRIES.map { it.cleanName }.toSet()
        for (entry in entries) {
            if (entry.cleanName !in seedCleanNames) {
                dynamicEntriesByKey[entry.cleanName] = entry
            }
        }
    }

    private fun inferSportCategory(lowerName: String): String {
        return when {
            lowerName.contains("cricket") || lowerName.contains("willow") ||
                lowerName.contains("t sports") || lowerName.contains("ptv") ||
                lowerName.contains("a sports") || lowerName.contains("dd sports") -> "Cricket"
            lowerName.contains("golazo") || lowerName.contains("fifa") ||
                lowerName.contains("madrid") || lowerName.contains("milan") ||
                lowerName.contains("inter tv") || lowerName.contains("sportitalia") ||
                lowerName.contains("bein") || lowerName.contains("tyc") ||
                lowerName.contains("telemundo") || lowerName.contains("a spor") -> "Football"
            lowerName.contains("fight") || lowerName.contains("combat") || lowerName.contains("boxing") -> "Fighting"
            lowerName.contains("racing") || lowerName.contains("racer") ||
                lowerName.contains("f1") || lowerName.contains("red bull") ||
                lowerName.contains("motor") -> "Motorsport"
            else -> "Sports"
        }
    }

    private fun normalizeChannelName(name: String): String {
        return name.lowercase(Locale.US)
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("[^a-z0-9+ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Automatically resolves dedicated match event streams (`PlayZ pro/<id>.txt` & `events.m3u8` / `playlist_s.m3u`)
     * FIRST, followed by verified TV broadcast channels for the given match.
     */
    fun enrichMatchWithAutoPlaylistStreams(match: LiveMatch): LiveMatch {
        val scheduleTimeLabel = extractShortTimeLabel(match.matchTime)
        val dedicatedMatchStreams = mutableListOf<StreamSource>()
        val matchedEntries = LinkedHashMap<String, PlaylistEntry>()
        val broadcastBadgeNames = mutableListOf<String>()

        // 0. If this is a direct PlayZ Server match, strictly preserve ONLY its dedicated PlayZ server streams (`pro/<id>.txt`)
        if (match.provider == "PLAYZ") {
            val pointer = playzMatchPointerById[match.id]?.removePrefix("/")
            val resolvedPro = if (pointer != null) playzResolvedStreamsByPointer[pointer] else null
            val realStreams = (resolvedPro ?: match.streams).filterNot {
                val cleanUrl = it.url.substringBefore("|").trim()
                cleanUrl.isBlank() ||
                    cleanUrl.endsWith(".txt", ignoreCase = true)
            }
            return match.copy(
                streams = realStreams,
                broadcastChannels = realStreams.map { it.name }.take(4)
            )
        }

        // 0a. Check if PlayZ Server (`events.txt` / `pro/<id>.txt`) has dedicated streams for this exact match
        val matchedPlayzEvent = findMatchingPlayzEvent(match)
        if (matchedPlayzEvent != null) {
            val pointer = playzMatchPointerById[matchedPlayzEvent.id]?.removePrefix("/")
            val resolvedPro = if (pointer != null) playzResolvedStreamsByPointer[pointer] else null
            val sourceStreams = resolvedPro?.takeIf { it.isNotEmpty() } ?: matchedPlayzEvent.streams
            for (ps in sourceStreams) {
                val isRawTxtPointer = ps.url.contains("pro/") && ps.url.endsWith(".txt", ignoreCase = true)
                if (ps.url.isNotBlank() && !isRawTxtPointer && dedicatedMatchStreams.none { it.url == ps.url && it.name == ps.name }) {
                    dedicatedMatchStreams.add(
                        ps.copy(
                            id = "${match.id}-${ps.id}",
                            isDefault = dedicatedMatchStreams.isEmpty(),
                            priorityOrder = dedicatedMatchStreams.size + 1,
                            isBackup = false
                        )
                    )
                    if (ps.name.isNotBlank() && ps.name !in broadcastBadgeNames) {
                        broadcastBadgeNames.add(ps.name)
                    }
                }
            }
        }

        // 0b. Check M3U live event feeds (`events.m3u8` / `playlist_s.m3u`) for `Team 1 vs Team 2` match streams
        val t1Token = normalizeTeamToken(match.team1Name)
        val t2Token = normalizeTeamToken(match.team2Name)
        if (t1Token.length >= 3 && t2Token.length >= 3) {
            for (evEntry in m3uEventEntries.values) {
                val entryNorm = normalizeTeamToken(evEntry.name)
                if (entryNorm.contains(t1Token) && entryNorm.contains(t2Token)) {
                    dedicatedMatchStreams.add(
                        StreamSource(
                            id = "${match.id}-${evEntry.id}",
                            name = evEntry.name,
                            quality = evEntry.quality,
                            url = evEntry.url,
                            userAgent = evEntry.userAgent,
                            referer = evEntry.referer,
                            origin = evEntry.origin,
                            isDefault = dedicatedMatchStreams.isEmpty(),
                            isEnabled = true,
                            priorityOrder = dedicatedMatchStreams.size + 1,
                            isBackup = false
                        )
                    )
                }
            }
        }

        fun addByCleanKey(cleanKey: String, broadcasterLabel: String? = null) {
            val found = dynamicEntriesByKey[cleanKey]
                ?: dynamicEntriesByKey.values.firstOrNull {
                    it.cleanName.contains(cleanKey) || cleanKey.contains(it.cleanName)
                }
            if (found != null && !matchedEntries.containsKey(found.url)) {
                val isKnownDead = streamHealthMap[found.url] == false
                if (!isKnownDead || matchedEntries.size < 2) {
                    matchedEntries[found.url] = found
                    val label = broadcasterLabel ?: found.name.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
                    if (label.isNotBlank() && label !in broadcastBadgeNames) {
                        broadcastBadgeNames.add(label)
                    }
                }
            }
        }

        // 1. Match explicit broadcast channels reported by ESPN or SofaScore
        for (rawBroadcaster in match.broadcastChannels) {
            val bLower = rawBroadcaster.lowercase(Locale.US)
            when {
                bLower.contains("espn") -> {
                    addByCleanKey("espn", rawBroadcaster)
                    addByCleanKey("espn hd", "ESPN HD")
                    addByCleanKey("espn 2", "ESPN 2")
                }
                bLower.contains("cbs") || bLower.contains("paramount") || bLower.contains("golazo") -> {
                    addByCleanKey("cbs sports golazo", "CBS Sports Golazo")
                }
                bLower.contains("bein") -> {
                    addByCleanKey("bein sports xtra hd", "beIN Sports HD")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("bein sports xtra", "beIN SPORTS XTRA")
                }
                bLower.contains("sony") || bLower.contains("ten") -> {
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("sony sports 5", "Sony Sports 5 HD")
                }
                bLower.contains("star") || bLower.contains("peacock") || bLower.contains("usa net") || bLower.contains("nbc") -> {
                    addByCleanKey("nbc sports", "NBC Sports")
                    addByCleanKey("star sports select 1", "Star Sports Select 1 HD")
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                }
                bLower.contains("fox") -> {
                    addByCleanKey("fox sports 1", "Fox Sports 1")
                    addByCleanKey("fox sports", "FOX Sports")
                }
                bLower.contains("tsn") -> {
                    addByCleanKey("tsn 2", "TSN 2")
                    addByCleanKey("tsn 3", "TSN 3")
                }
                bLower.contains("tyc") -> {
                    addByCleanKey("tyc sports", "TyC Sports")
                }
                bLower.contains("telemundo") -> {
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                }
                bLower.contains("fubo") -> {
                    addByCleanKey("fubo sports", "Fubo Sports")
                }
                bLower.contains("dazn") || bLower.contains("sportitalia") -> {
                    addByCleanKey("sportitalia", "Sportitalia HD")
                }
                bLower.contains("willow") -> {
                    addByCleanKey("willow tv", "Willow TV HD")
                    addByCleanKey("willow sports", "Willow Sports")
                }
                bLower.contains("t sports") || bLower.contains("tsports") -> {
                    addByCleanKey("t sports", "T Sports HD")
                    addByCleanKey("t sports 2", "T Sports (Server 2)")
                }
                bLower.contains("a sports") -> {
                    addByCleanKey("a sports", "A Sports HD")
                }
                bLower.contains("ptv") -> {
                    addByCleanKey("ptv sports", "PTV Sports HD")
                }
                else -> {
                    val norm = normalizeChannelName(rawBroadcaster)
                    if (norm.length >= 3) {
                        addByCleanKey(norm, rawBroadcaster)
                    }
                }
            }
        }

        // 2. Auto-Detector by SportType, Competition (`competitionId`), and Participating Teams
        if (match.sport == SportType.FOOTBALL) {
            val comp = match.competitionId.lowercase(Locale.US)
            val teamsLower = "${match.team1Name} ${match.team2Name}".lowercase(Locale.US)

            if (teamsLower.contains("real madrid")) {
                addByCleanKey("real madrid tv", "RealMadrid TV")
            }

            when {
                comp == "esp.1" || comp == "esp.2" || comp == "esp.copa_del_rey" -> {
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("bein sports xtra en espanol", "beIN Sports XTRA Español")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("espn", "ESPN LaLiga")
                    addByCleanKey("tsn 2", "TSN 2 HD")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                    addByCleanKey("fifa+ spain", "FIFA+ Spain")
                }
                comp in setOf("arg.1", "conmebol.libertadores", "conmebol.sudamericana", "fifa.worldq.conmebol", "conmebol.america") -> {
                    addByCleanKey("tyc sports", "TyC Sports")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("espn premium", "ESPN Premium HD")
                    addByCleanKey("bein sports xtra en espanol", "beIN Sports XTRA Español")
                    addByCleanKey("fox sports", "FOX Sports")
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                }
                comp in setOf("mex.1", "usa.1", "concacaf.gold", "fifa.worldq.concacaf") -> {
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                    addByCleanKey("fox sports 1", "Fox Sports 1")
                    addByCleanKey("fox sports", "FOX Sports")
                    addByCleanKey("fubo sports", "Fubo Sports")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("tsn 2", "TSN 2 HD")
                    addByCleanKey("nbc sports", "NBC Sports")
                }
                comp == "por.1" || comp == "ned.1" || comp == "bra.1" -> {
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("sportitalia", "Sportitalia FHD")
                    addByCleanKey("fubo sports", "Fubo Sports")
                    addByCleanKey("cbs sports golazo", "CBS Sports Golazo")
                }
                comp == "eng.1" || comp == "eng.fa" || comp == "sco.1" -> {
                    addByCleanKey("nbc sports", "NBC Sports")
                    addByCleanKey("telemundo deportes", "Telemundo Deportes")
                    addByCleanKey("tsn 2", "TSN 2 HD")
                    addByCleanKey("tsn 3", "TSN 3 HD")
                    addByCleanKey("star sports select 1", "Star Sports Select 1 HD")
                    addByCleanKey("star sports select 2", "Star Sports Select 2 HD")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("talk sport", "Talk Sport")
                }
                comp.startsWith("uefa.") -> {
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("sony sports 5", "Sony Sports 5 HD")
                    addByCleanKey("cbs sports golazo", "CBS Sports Golazo")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("tsn 2", "TSN 2 HD")
                }
                comp == "ita.1" || comp == "ita.coppa_italia" -> {
                    addByCleanKey("sportitalia", "Sportitalia FHD")
                    addByCleanKey("cbs sports golazo", "CBS Sports Golazo")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("tsn 3", "TSN 3 HD")
                    addByCleanKey("fifa+ italy", "FIFA+ Italy")
                }
                comp == "ger.1" || comp == "ger.dfb_pokal" || comp == "aut.1" || comp == "sui.1" -> {
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("sony sports 5", "Sony Sports 5 HD")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("fox sports 1", "Fox Sports 1")
                    addByCleanKey("fifa+ german", "FIFA+ Germany")
                }
                comp == "fra.1" || comp == "fra.coupe_de_france" || comp == "bel.1" -> {
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("bein sports xtra", "beIN SPORTS XTRA")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("fifa+ french", "FIFA+ France")
                }
                comp == "tur.1" -> {
                    addByCleanKey("a spor", "A SPOR")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("espn", "ESPN HD")
                }
                comp == "ksa.1" || comp == "afc.cup" || comp == "fifa.worldq.afc" -> {
                    addByCleanKey("saudia arabia", "Saudia Arabia Sports")
                    addByCleanKey("ktv sport plus", "KTV Sport Plus")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("fox sports 1", "Fox Sports 1")
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                }
                else -> {
                    addByCleanKey("bein sports xtra hd", "Bein Sports Xtra HD")
                    addByCleanKey("espn", "ESPN HD")
                    addByCleanKey("sony sports 2", "Sony Sports 2 HD")
                    addByCleanKey("bein sport 1", "Bein Sport 1")
                    addByCleanKey("cbs sports golazo", "CBS Sports Golazo")
                    addByCleanKey("fubo sports", "Fubo Sports")
                    addByCleanKey("tsn 2", "TSN 2 HD")
                }
            }
        } else if (match.sport == SportType.CRICKET) {
            val teamsLower = "${match.team1Name} ${match.team2Name}".lowercase(Locale.US)
            when {
                teamsLower.contains("bangladesh") -> {
                    addByCleanKey("t sports", "T Sports HD")
                    addByCleanKey("t sports 2", "T Sports (Server 2)")
                    addByCleanKey("a sports", "A Sports HD")
                    addByCleanKey("willow tv", "Willow TV HD")
                    addByCleanKey("willow sports", "Willow Sports")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("star sports 2", "Star Sports 2 HD")
                    addByCleanKey("ptv sports", "PTV Sports HD")
                }
                teamsLower.contains("pakistan") -> {
                    addByCleanKey("a sports", "A Sports HD")
                    addByCleanKey("a sports 2", "A Sports (Server 2)")
                    addByCleanKey("ptv sports", "PTV Sports HD")
                    addByCleanKey("willow tv", "Willow TV HD")
                    addByCleanKey("willow sports", "Willow Sports")
                    addByCleanKey("t sports", "T Sports HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("cricket gold", "Cricket Gold HD")
                }
                teamsLower.contains("india") -> {
                    addByCleanKey("star sports 2", "Star Sports 2 HD")
                    addByCleanKey("live sports", "Live Sports HD")
                    addByCleanKey("dd sports", "DD Sports HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("sony sports 5", "Sony Sports 5 HD")
                    addByCleanKey("willow tv", "Willow TV HD")
                    addByCleanKey("willow sports", "Willow Sports")
                    addByCleanKey("t sports", "T Sports HD")
                }
                else -> {
                    addByCleanKey("willow tv", "Willow TV HD")
                    addByCleanKey("willow sports", "Willow Sports")
                    addByCleanKey("t sports", "T Sports HD")
                    addByCleanKey("a sports", "A Sports HD")
                    addByCleanKey("ptv sports", "PTV Sports HD")
                    addByCleanKey("star sports 2", "Star Sports 2 HD")
                    addByCleanKey("sony sports 1", "Sony Sports 1 HD")
                    addByCleanKey("sony sports 5", "Sony Sports 5 HD")
                    addByCleanKey("cricket gold", "Cricket Gold HD")
                    addByCleanKey("live sports", "Live Sports HD")
                    addByCleanKey("dd sports", "DD Sports HD")
                }
            }
        } else if (match.sport == SportType.BOXING) {
            addByCleanKey("fight network", "Fight Network HD")
            addByCleanKey("tnt sports", "TNT Sports HD")
            addByCleanKey("sony sports 1", "Sony Sports 1 HD")
        } else if (match.sport == SportType.RACING) {
            addByCleanKey("motor vision", "Motor Vision HD")
            addByCleanKey("red bull tv", "Red Bull TV")
            addByCleanKey("fox sports 501", "Fox Sports 501")
        } else {
            addByCleanKey("espn", "ESPN HD")
            addByCleanKey("tsn 2", "TSN 2 HD")
            addByCleanKey("bein sports xtra hd", "beIN Sports HD")
        }

        // Include any Admin-added custom M3U entries matching this sport
        for ((cleanKey, customEntry) in adminCustomEntries) {
            if (streamHealthMap[customEntry.url] == false) continue
            val isCricketEntry = customEntry.category.equals("Cricket", true) ||
                customEntry.category.equals("Bangla Sports", true) ||
                customEntry.category.equals("Pakistan", true) ||
                customEntry.category.equals("SonyLiv Live", true)
            val isFootballEntry = customEntry.category.equals("Football", true) ||
                customEntry.category.equals("Sports", true) ||
                customEntry.category.equals("SonyLiv Live", true) ||
                customEntry.category.equals("Bangla Sports", true)
            if ((match.sport == SportType.CRICKET && isCricketEntry) ||
                (match.sport == SportType.FOOTBALL && isFootballEntry)
            ) {
                matchedEntries.putIfAbsent(cleanKey, customEntry)
            }
        }

        val sortedEntries = matchedEntries.values.sortedWith(
            compareByDescending<PlaylistEntry> { streamHealthMap[it.url] == true }
                .thenBy { streamHealthMap[it.url] == false }
                .thenBy { it.isBackup }
        )

        val offset = dedicatedMatchStreams.size
        val channelFallbackStreams = sortedEntries.mapIndexed { index, entry ->
            val isVerifiedOnline = streamHealthMap[entry.url] == true
            val isKnownOffline = streamHealthMap[entry.url] == false
            val slotTag = when {
                match.status == MatchStatus.LIVE -> if (isVerifiedOnline) "LIVE • ACTIVE" else "LIVE NOW"
                scheduleTimeLabel.isNotBlank() -> scheduleTimeLabel
                else -> "Auto-Matched"
            }
            StreamSource(
                id = "${match.id}-${entry.id}",
                name = "${entry.name} • $slotTag",
                quality = entry.quality,
                url = entry.url,
                userAgent = entry.userAgent,
                referer = entry.referer,
                origin = entry.origin,
                drmLicense = entry.drmLicense,
                isDefault = offset == 0 && index == 0,
                isEnabled = true,
                priorityOrder = offset + index + 1,
                isBackup = offset > 0 || isKnownOffline || (entry.isBackup && !isVerifiedOnline) || index >= 5
            )
        }

        val rawCombined = (dedicatedMatchStreams + channelFallbackStreams)
            .distinctBy { "${it.name}_${it.url}" }

        // Put non-DRM .m3u8 streams ahead of .mpd DRM streams so default server plays cleanly without DrmHalHidl/CryptoHalHidl errors
        val combinedStreams = rawCombined
            .sortedWith(
                compareBy<StreamSource> { !it.drmLicense.isNullOrBlank() || it.url.substringBefore("|").contains(".mpd", true) }
                    .thenBy { it.isBackup }
                    .thenBy { it.priorityOrder }
            )
            .mapIndexed { idx, s ->
                s.copy(
                    isDefault = idx == 0,
                    priorityOrder = idx + 1,
                    isBackup = idx >= 6 && s.isBackup
                )
            }

        val finalBroadcastList = if (broadcastBadgeNames.isNotEmpty()) {
            broadcastBadgeNames.take(4)
        } else {
            match.broadcastChannels
        }

        return match.copy(
            streams = if (combinedStreams.isNotEmpty()) combinedStreams else match.streams,
            broadcastChannels = finalBroadcastList
        )
    }

    fun buildAutoUpdatedChannels(favoriteChannelIds: Set<String>): List<SportsChannel> {
        val activeEntries = getAllActivePlaylistEntries()
            .sortedWith(
                compareByDescending<PlaylistEntry> { streamHealthMap[it.url] == true }
                    .thenBy { it.isBackup }
            )
        return activeEntries.mapIndexed { idx, entry ->
            val shortLogoText = entry.name
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.take(2).uppercase(Locale.US) }
                .take(4)
                .ifBlank { "TV" }

            SportsChannel(
                id = entry.id,
                name = entry.name,
                category = entry.category,
                logoText = shortLogoText,
                logoUrl = entry.logoUrl,
                isFavorite = entry.id in favoriteChannelIds,
                isEnabled = true,
                streams = listOf(
                    StreamSource(
                        id = "${entry.id}-s1",
                        name = "${entry.name} (${entry.quality})",
                        quality = entry.quality,
                        url = entry.url,
                        userAgent = entry.userAgent,
                        referer = entry.referer,
                        origin = entry.origin,
                        drmLicense = entry.drmLicense,
                        isDefault = true,
                        isEnabled = true,
                        priorityOrder = idx + 1,
                        isBackup = entry.isBackup
                    )
                )
            )
        }
    }

    private fun extractShortTimeLabel(matchTime: String): String {
        val trimmed = matchTime.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.contains("|")) {
            val timePart = trimmed.substringBefore("|").trim()
            if (timePart.isNotBlank()) return "$timePart BST"
        }
        val bstMatch = Regex("""(\d{1,2}:\d{2}\s*[AP]M\s*BST)""", RegexOption.IGNORE_CASE).find(trimmed)
        if (bstMatch != null) {
            return bstMatch.groupValues[1].uppercase(Locale.US)
        }
        return ""
    }
}
