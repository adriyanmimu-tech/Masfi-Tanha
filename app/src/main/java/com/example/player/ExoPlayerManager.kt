package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.data.model.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.URLDecoder
import java.util.Collections
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
class ExoPlayerManager(private val context: Context) {

    companion object {
        @Volatile
        private var sharedMediaCache: SimpleCache? = null
        private val cacheLock = Any()

        // Dedicated 8-thread pool for parallel HTTP 206 byte-range segment acceleration on throttled Highlight CDNs
        private val parallelSegmentExecutor = Executors.newFixedThreadPool(8) { r ->
            Thread(r, "PlayLive-ParallelSeg").apply { isDaemon = true }
        }

        fun getOrCreateDiskCache(context: Context): SimpleCache? {
            sharedMediaCache?.let { return it }
            synchronized(cacheLock) {
                sharedMediaCache?.let { return it }
                return runCatching {
                    val cacheDir = File(context.applicationContext.cacheDir, "playlive_stream_cache_v1")
                    val evictor = LeastRecentlyUsedCacheEvictor(150L * 1024L * 1024L) // 150 MB high-speed disk cache
                    val dbProvider = StandaloneDatabaseProvider(context.applicationContext)
                    SimpleCache(cacheDir, evictor, dbProvider).also { sharedMediaCache = it }
                }.getOrNull()
            }
        }
    }

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentStream = MutableStateFlow<StreamSource?>(null)
    val currentStream: StateFlow<StreamSource?> = _currentStream.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isLowQualityMode = MutableStateFlow(false)
    val isLowQualityMode: StateFlow<Boolean> = _isLowQualityMode.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var stallWatchdogJob: Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var fallbackCandidates: List<StreamSource> = emptyList()
    private var failedStreamUrls = mutableSetOf<String>()
    private val preWarmedHosts = Collections.synchronizedSet(LinkedHashSet<String>())
    private val dnsCache = java.util.concurrent.ConcurrentHashMap<String, List<InetAddress>>()
    private var currentForceLowQuality: Boolean = false
    private var sharedBandwidthMeter: DefaultBandwidthMeter? = null
    private var hasReachedReadyForCurrentStream: Boolean = false

    private fun isVodOrHighlightStream(stream: StreamSource?): Boolean {
        if (stream == null) return false
        val cleanUrl = stream.url.substringBefore("|").trim().lowercase(Locale.US)
        val idLower = stream.id.lowercase(Locale.US)
        val nameLower = stream.name.lowercase(Locale.US)
        return cleanUrl.contains(".mp4") ||
            cleanUrl.contains(".mkv") ||
            cleanUrl.contains(".webm") ||
            idLower.startsWith("pz-hl-") ||
            idLower.startsWith("highlight") ||
            nameLower.contains("highlight") ||
            nameLower.contains("recap") ||
            nameLower.contains("1st half") ||
            nameLower.contains("2nd half")
    }

    // Fast in-memory DNS cache + IPv4 preference to avoid slow DNS lookups on stream/segment switches
    private val fastDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            dnsCache[hostname]?.takeIf { it.isNotEmpty() }?.let { return it }
            val resolved = Dns.SYSTEM.lookup(hostname).sortedBy { addr ->
                if (addr.address.size == 4) 0 else 1 // Prefer IPv4 first for lower streaming CDN latency
            }
            if (resolved.isNotEmpty()) {
                dnsCache[hostname] = resolved
            }
            return resolved
        }
    }

    // Shared high-performance OkHttpClient (matching j0xdkn.apk Lf4/a0 -> OkHttpDataSource.Factory):
    // Does NOT strip Content-Length or mutate segment bytes because Media3's TsExtractor (Lb4/f0)
    // natively scans up to 940 bytes for 0x47 sync bytes (automatically skipping 120-byte PNG headers
    // on tiktokcdn .image segments and reading .woff2/.css MPEG-TS segments with intact Content-Length).
    private val streamHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(fastDns)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Multi-Connection HTTP 206 Byte-Range Accelerator for throttled Highlight CDNs
     * (e.g. `s1q2105.com` / `soccerfull.net` `.woff2` segments which throttle single connections to ~35-65 KB/s).
     * Splits each segment into 256 KB byte ranges downloaded across 8 parallel connections (~4+ Mbps),
     * while returning the first 256 KB chunk immediately so ExoPlayer starts rendering in < 0.4s.
     */
    private inner class ParallelHighlightSegmentDataSource(
        private val delegate: DataSource,
        private val userAgent: String,
        private val defaultHeaders: Map<String, String>
    ) : DataSource {

        private val transferListeners = mutableListOf<TransferListener>()
        private var currentDataSpec: DataSpec? = null
        private var activeParallelState: ParallelDownloadState? = null
        private var directFallbackStream: InputStream? = null
        private var directBytesRemaining: Long = C.LENGTH_UNSET.toLong()
        private var usingDelegate: Boolean = false
        private var transferStarted: Boolean = false

        override fun addTransferListener(transferListener: TransferListener) {
            delegate.addTransferListener(transferListener)
            transferListeners.add(transferListener)
        }

        private fun shouldAccelerateSegment(dataSpec: DataSpec): Boolean {
            if (dataSpec.position != 0L || dataSpec.length != C.LENGTH_UNSET.toLong()) return false
            val uriStr = dataSpec.uri.toString().lowercase(Locale.US)
            val path = dataSpec.uri.path?.lowercase(Locale.US).orEmpty()
            if (path.endsWith(".m3u8") || path.endsWith(".mpd") || uriStr.contains(".m3u8")) return false

            // Always accelerate disguised .woff2/.woff/.ttf segments and s1q / soccerfull highlight CDNs
            if (path.endsWith(".woff2") ||
                path.endsWith(".woff") ||
                path.endsWith(".ttf") ||
                uriStr.contains("s1q") ||
                uriStr.contains("soccerfull")
            ) {
                return true
            }

            // Also accelerate HLS segments when playing a Highlight / Full-Match Replay stream
            if (isVodOrHighlightStream(_currentStream.value) &&
                (path.endsWith(".ts") || path.endsWith(".css") || path.endsWith(".js"))
            ) {
                return true
            }
            return false
        }

        override fun open(dataSpec: DataSpec): Long {
            currentDataSpec = dataSpec
            usingDelegate = false
            transferStarted = false
            activeParallelState = null
            directFallbackStream = null

            if (!shouldAccelerateSegment(dataSpec)) {
                usingDelegate = true
                return delegate.open(dataSpec)
            }

            val chunkSize = 256 * 1024 // 256 KB per parallel range chunk
            val url = dataSpec.uri.toString()

            return try {
                for (listener in transferListeners) {
                    listener.onTransferInitializing(this, dataSpec, true)
                }

                val firstReqBuilder = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", userAgent)
                    .header("Range", "bytes=0-${chunkSize - 1}")
                for ((k, v) in defaultHeaders) {
                    firstReqBuilder.header(k, v)
                }
                for ((k, v) in dataSpec.httpRequestHeaders) {
                    firstReqBuilder.header(k, v)
                }

                val firstResp = streamHttpClient.newCall(firstReqBuilder.build()).execute()
                val code = firstResp.code

                if (code == 206) {
                    val contentRange = firstResp.header("Content-Range").orEmpty()
                    // Example: "bytes 0-262143/1371648"
                    val totalSize = contentRange.substringAfterLast('/', "").trim().toLongOrNull() ?: -1L
                    val firstBytes = firstResp.body?.use { it.bytes() } ?: ByteArray(0)

                    if (totalSize <= 0L || firstBytes.isEmpty()) {
                        usingDelegate = true
                        return delegate.open(dataSpec)
                    }

                    val numChunks = ((totalSize + chunkSize - 1) / chunkSize).toInt()
                    val cancelled = AtomicBoolean(false)
                    val chunkFutures = Array<CompletableFuture<ByteArray>>(numChunks) { idx ->
                        if (idx == 0) {
                            CompletableFuture.completedFuture(firstBytes)
                        } else {
                            val startByte = idx.toLong() * chunkSize
                            val endByte = minOf(totalSize - 1L, startByte + chunkSize - 1L)
                            CompletableFuture.supplyAsync({
                                if (cancelled.get()) throw IOException("Cancelled")
                                fetchByteRangeWithRetry(url, dataSpec, startByte, endByte, cancelled)
                            }, parallelSegmentExecutor)
                        }
                    }

                    activeParallelState = ParallelDownloadState(
                        totalSize = totalSize,
                        chunkSize = chunkSize,
                        chunkFutures = chunkFutures,
                        cancelled = cancelled
                    )
                    transferStarted = true
                    for (listener in transferListeners) {
                        listener.onTransferStart(this, dataSpec, true)
                    }
                    totalSize
                } else if (code == 200) {
                    val body = firstResp.body
                    if (body == null) {
                        firstResp.close()
                        usingDelegate = true
                        return delegate.open(dataSpec)
                    }
                    val contentLen = body.contentLength()
                    directFallbackStream = body.byteStream()
                    directBytesRemaining = if (contentLen >= 0L) contentLen else C.LENGTH_UNSET.toLong()
                    transferStarted = true
                    for (listener in transferListeners) {
                        listener.onTransferStart(this, dataSpec, true)
                    }
                    directBytesRemaining
                } else {
                    firstResp.close()
                    usingDelegate = true
                    delegate.open(dataSpec)
                }
            } catch (_: Exception) {
                activeParallelState?.cancel()
                activeParallelState = null
                runCatching { directFallbackStream?.close() }
                directFallbackStream = null
                usingDelegate = true
                delegate.open(dataSpec)
            }
        }

        private fun fetchByteRangeWithRetry(
            url: String,
            dataSpec: DataSpec,
            startByte: Long,
            endByte: Long,
            cancelled: AtomicBoolean
        ): ByteArray {
            var lastError: Exception? = null
            for (attempt in 1..3) {
                if (cancelled.get()) throw IOException("Cancelled")
                try {
                    val reqBuilder = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", userAgent)
                        .header("Range", "bytes=$startByte-$endByte")
                    for ((k, v) in defaultHeaders) {
                        reqBuilder.header(k, v)
                    }
                    for ((k, v) in dataSpec.httpRequestHeaders) {
                        reqBuilder.header(k, v)
                    }
                    streamHttpClient.newCall(reqBuilder.build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            throw IOException("HTTP ${resp.code} for range $startByte-$endByte")
                        }
                        val bytes = resp.body?.bytes() ?: throw IOException("Empty range body")
                        if (bytes.isEmpty()) throw IOException("Zero bytes in range $startByte-$endByte")
                        return bytes
                    }
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IOException("Failed range $startByte-$endByte after retries", lastError)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (usingDelegate) {
                return delegate.read(buffer, offset, length)
            }
            if (length == 0) return 0

            val parallel = activeParallelState
            if (parallel != null) {
                val bytesRead = parallel.read(buffer, offset, length)
                if (bytesRead > 0) {
                    val spec = currentDataSpec
                    if (spec != null) {
                        for (listener in transferListeners) {
                            listener.onBytesTransferred(this, spec, true, bytesRead)
                        }
                    }
                }
                return bytesRead
            }

            val stream = directFallbackStream ?: return C.RESULT_END_OF_INPUT
            val readCount = stream.read(buffer, offset, length)
            if (readCount == -1) {
                return C.RESULT_END_OF_INPUT
            }
            if (directBytesRemaining != C.LENGTH_UNSET.toLong()) {
                directBytesRemaining -= readCount
            }
            val spec = currentDataSpec
            if (spec != null && readCount > 0) {
                for (listener in transferListeners) {
                    listener.onBytesTransferred(this, spec, true, readCount)
                }
            }
            return readCount
        }

        override fun getUri(): Uri? {
            return if (usingDelegate) delegate.uri else currentDataSpec?.uri
        }

        override fun close() {
            if (usingDelegate) {
                delegate.close()
                usingDelegate = false
                return
            }
            activeParallelState?.cancel()
            activeParallelState = null
            runCatching { directFallbackStream?.close() }
            directFallbackStream = null
            if (transferStarted) {
                transferStarted = false
                val spec = currentDataSpec
                if (spec != null) {
                    for (listener in transferListeners) {
                        listener.onTransferEnd(this, spec, true)
                    }
                }
            }
            currentDataSpec = null
        }
    }

    private class ParallelDownloadState(
        val totalSize: Long,
        val chunkSize: Int,
        val chunkFutures: Array<CompletableFuture<ByteArray>>,
        val cancelled: AtomicBoolean
    ) {
        private var globalPosition: Long = 0L

        fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (globalPosition >= totalSize) return C.RESULT_END_OF_INPUT
            val chunkIdx = (globalPosition / chunkSize).toInt().coerceIn(0, chunkFutures.lastIndex)
            val chunkBytes = try {
                chunkFutures[chunkIdx].get(25, TimeUnit.SECONDS)
            } catch (e: Exception) {
                throw IOException("Parallel highlight chunk $chunkIdx failed", e)
            }
            val offsetInChunk = (globalPosition % chunkSize).toInt()
            val availableInChunk = chunkBytes.size - offsetInChunk
            if (availableInChunk <= 0) return C.RESULT_END_OF_INPUT
            val toCopy = minOf(length, availableInChunk)
            System.arraycopy(chunkBytes, offsetInChunk, buffer, offset, toCopy)
            globalPosition += toCopy
            return toCopy
        }

        fun cancel() {
            cancelled.set(true)
            for (future in chunkFutures) {
                if (!future.isDone) {
                    future.cancel(true)
                }
            }
        }
    }

    // Fast chunk retry policy so transient network drops never freeze Highlights or Live streams
    private val resilientLoadErrorPolicy: LoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(12) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val errorCount = loadErrorInfo.errorCount
            return when {
                errorCount <= 3 -> 300L
                errorCount <= 6 -> 700L
                else -> 1500L
            }
        }

        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 12
    }

    /**
     * Pre-warms DNS resolution in the background without opening competing HTTP byte-range sockets
     * that could throttle single-connection Highlight (.mp4) or HLS servers.
     */
    fun preWarmStreams(streams: List<StreamSource>) {
        if (streams.isEmpty()) return
        ioScope.launch {
            streams.asSequence()
                .filter { it.isEnabled && it.url.isNotBlank() }
                .take(5)
                .forEach { stream ->
                    val cleanUrl = stream.url.substringBefore("|").trim().removeSuffix("?")
                    val host = runCatching { Uri.parse(cleanUrl).host }.getOrNull()
                    if (!host.isNullOrBlank() && preWarmedHosts.add(host)) {
                        launch {
                            runCatching { fastDns.lookup(host) }
                        }
                    }
                }
        }
    }

    fun setFallbackCandidates(candidates: List<StreamSource>, forceLowQuality: Boolean = false) {
        val valid = candidates.filter { it.isEnabled && it.url.isNotBlank() }
        fallbackCandidates = valid
        currentForceLowQuality = forceLowQuality
        _isLowQualityMode.value = forceLowQuality
        preWarmStreams(valid)
    }

    fun toggleLowQualityMode(): Boolean {
        val next = !_isLowQualityMode.value
        setLowQualityMode(next)
        return next
    }

    fun setLowQualityMode(enabled: Boolean) {
        currentForceLowQuality = enabled
        _isLowQualityMode.value = enabled
        exoPlayer?.let { player ->
            val builder = player.trackSelectionParameters.buildUpon()
            if (enabled) {
                builder.setMaxVideoSizeSd()
                builder.setMaxVideoBitrate(800_000)
            } else {
                builder.clearVideoSizeConstraints()
                builder.setMaxVideoBitrate(Int.MAX_VALUE)
            }
            player.trackSelectionParameters = builder.build()
        }
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            // Match j0xdkn.apk (PlayerActivity.B): EXTENSION_RENDERER_MODE_ON (1) prioritizes hardware MediaCodec first
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            // Match reference APK (j0xdkn.apk) DefaultTrackSelector + AdaptiveTrackSelection
            val trackSelector = DefaultTrackSelector(
                context,
                AdaptiveTrackSelection.Factory()
            ).apply {
                setParameters(
                    buildUponParameters()
                        .setExceedVideoConstraintsIfNecessary(true)
                        .setExceedRendererCapabilitiesIfNecessary(true)
                )
            }

            // Reference APK (Ld7/e in j0xdkn.apk) Buffer Window + Fast Highlight Start:
            // - 60s minBuffer / 120s maxBuffer continuously pre-fetches segments up to 2 minutes ahead
            // - 1.2s start buffer begins decoding as soon as the first ~256 KB of a segment arrives
            // - 2.5s re-buffer cushion recovers rapidly with 128 MB target buffer cap
            val loadControl = DefaultLoadControl.Builder()
                .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
                .setBufferDurationsMs(
                    60_000,  // min buffer 60s ahead (matches Ld7/e in j0xdkn.apk)
                    120_000, // max buffer 120s ahead (2 mins deep pre-buffer for highlights & live)
                    1_200,   // fast playback start buffer 1.2s (renders first frame immediately on large segments)
                    2_500    // fast re-buffer recovery 2.5s
                )
                .setTargetBufferBytes(128 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30_000, true)
                .build()

            val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(2_200_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_UNKNOWN, 2_200_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_WIFI, 2_500_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_2G, 500_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_3G, 1_500_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_4G, 2_200_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_SA, 3_000_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_5G_NSA, 3_000_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_CELLULAR_UNKNOWN, 2_000_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_ETHERNET, 3_000_000L)
                .setInitialBitrateEstimate(C.NETWORK_TYPE_OTHER, 2_200_000L)
                .setResetOnNetworkTypeChange(true)
                .build()
            sharedBandwidthMeter = bandwidthMeter

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setBandwidthMeter(bandwidthMeter)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .build()
                .apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    volume = if (_isMuted.value) 0.0f else 1.0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    _isBuffering.value = true
                                    _errorMessage.value = null
                                    if (!hasReachedReadyForCurrentStream) {
                                        startStallWatchdog()
                                    }
                                }
                                Player.STATE_READY -> {
                                    hasReachedReadyForCurrentStream = true
                                    _isBuffering.value = false
                                    _errorMessage.value = null
                                    stopStallWatchdog()
                                    _duration.value = duration.coerceAtLeast(0L)
                                    startPositionTracking()
                                }
                                Player.STATE_ENDED -> {
                                    _isBuffering.value = false
                                    _isPlaying.value = false
                                    stopStallWatchdog()
                                    stopPositionTracking()
                                }
                                Player.STATE_IDLE -> {
                                    _isBuffering.value = false
                                    stopStallWatchdog()
                                    stopPositionTracking()
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                hasReachedReadyForCurrentStream = true
                                _isBuffering.value = false
                                stopStallWatchdog()
                                startPositionTracking()
                            } else {
                                stopPositionTracking()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            stopStallWatchdog()
                            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                                exoPlayer?.let { p ->
                                    p.seekToDefaultPosition()
                                    p.prepare()
                                    p.playWhenReady = true
                                }
                                return
                            }
                            // For transient network/IO timeout on VOD/Highlights, try seamless in-place resume once before switching
                            val isNetworkGlitch = error.cause is IOException ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                            val currentUrl = _currentStream.value?.url.orEmpty()
                            val retryKey = "retry_once:$currentUrl"
                            if (isNetworkGlitch && currentUrl.isNotBlank() && retryKey !in failedStreamUrls) {
                                failedStreamUrls.add(retryKey)
                                exoPlayer?.let { p ->
                                    val savedPos = p.currentPosition.coerceAtLeast(0L)
                                    p.prepare()
                                    if (p.duration > 0L && savedPos > 0L) {
                                        p.seekTo(savedPos)
                                    }
                                    p.playWhenReady = true
                                }
                                return
                            }

                            _isBuffering.value = false
                            _isPlaying.value = false
                            stopPositionTracking()
                            if (currentUrl.isNotBlank()) {
                                failedStreamUrls.add(currentUrl)
                            }
                            val nextStream = fallbackCandidates.firstOrNull { candidate ->
                                candidate.url.isNotBlank() && candidate.url !in failedStreamUrls
                            }
                            if (nextStream != null) {
                                playStreamInternal(nextStream, currentForceLowQuality, resetFailedSet = false)
                            } else {
                                val cause = error.message ?: "Unknown playback error"
                                _errorMessage.value = "Playback failed: $cause. Please switch stream server."
                            }
                        }
                    })
                }
        }
        return exoPlayer!!
    }

    /**
     * Initial-Connection Watchdog:
     * - Only runs before a stream has reached STATE_READY for the first time.
     * - Never interrupts an active stream mid-match during normal buffering recovery.
     */
    private fun startStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = scope.launch {
            val activeStream = _currentStream.value
            val isVod = isVodOrHighlightStream(activeStream)
            val waitMs = if (isVod) 18_000L else 15_000L
            delay(waitMs)

            val p = exoPlayer ?: return@launch
            if (!hasReachedReadyForCurrentStream &&
                p.playbackState == Player.STATE_BUFFERING &&
                p.playWhenReady &&
                fallbackCandidates.size > 1
            ) {
                val currentUrl = _currentStream.value?.url.orEmpty()
                if (currentUrl.isNotBlank()) failedStreamUrls.add(currentUrl)
                val nextCandidate = fallbackCandidates.firstOrNull { candidate ->
                    candidate.url.isNotBlank() && candidate.url !in failedStreamUrls
                }
                if (nextCandidate != null) {
                    playStreamInternal(nextCandidate, currentForceLowQuality, resetFailedSet = false)
                }
            }
        }
    }

    private fun stopStallWatchdog() {
        stallWatchdogJob?.cancel()
        stallWatchdogJob = null
    }

    private fun startPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                exoPlayer?.let { p ->
                    _currentPosition.value = p.currentPosition.coerceAtLeast(0L)
                    _duration.value = p.duration.coerceAtLeast(0L)
                }
                delay(500)
            }
        }
    }

    private fun stopPositionTracking() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun toggleMute() {
        val newMuted = !_isMuted.value
        _isMuted.value = newMuted
        exoPlayer?.volume = if (newMuted) 0.0f else 1.0f
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        exoPlayer?.volume = if (muted) 0.0f else 1.0f
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun playStream(stream: StreamSource, forceLowQuality: Boolean = currentForceLowQuality) {
        playStreamInternal(stream, forceLowQuality, resetFailedSet = true)
    }

    private val noOpDrmProvider = DrmSessionManagerProvider {
        androidx.media3.exoplayer.drm.DrmSessionManager.DRM_UNSUPPORTED
    }

    private fun playStreamInternal(
        stream: StreamSource,
        forceLowQuality: Boolean,
        resetFailedSet: Boolean
    ) {
        if (stream.url.isBlank()) {
            _isBuffering.value = false
            _errorMessage.value = "Stream URL is empty. Please select another server."
            return
        }
        if (resetFailedSet) {
            failedStreamUrls.clear()
        }
        hasReachedReadyForCurrentStream = false
        currentForceLowQuality = forceLowQuality
        _isLowQualityMode.value = forceLowQuality

        try {
            val player = getPlayer()
            _currentStream.value = stream
            _errorMessage.value = null
            _isBuffering.value = true

            player.trackSelectionParameters = if (forceLowQuality) {
                player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSizeSd()
                    .setMaxVideoBitrate(800_000)
                    .build()
            } else {
                player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .build()
            }

            val mediaSource = createMediaSource(stream, forceLowQuality)
            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _isBuffering.value = false
            _errorMessage.value = "Failed to initialize stream: ${e.localizedMessage ?: "Invalid URL"}"
        }
    }

    private data class ParsedStreamSpec(
        val cleanUrl: String,
        val userAgent: String,
        val headers: Map<String, String>,
        val drmLicense: String?,
        val drmScheme: String?
    )

    private fun parseStreamSpec(stream: StreamSource): ParsedStreamSpec {
        val raw = stream.url.trim()
        val cleanUrl = raw.substringBefore("|").trim().removeSuffix("?")
        val pipePart = if (raw.contains("|")) raw.substringAfter("|").trim() else ""

        var resolvedUserAgent = stream.userAgent?.takeIf { it.isNotBlank() }
        val headers = LinkedHashMap<String, String>()

        stream.referer?.takeIf { it.isNotBlank() }?.let { headers["Referer"] = it }
        stream.origin?.takeIf { it.isNotBlank() }?.let { headers["Origin"] = it }
        stream.cookie?.takeIf { it.isNotBlank() }?.let { headers["Cookie"] = it }

        var resolvedDrmLicense = stream.drmLicense?.takeIf { it.isNotBlank() }
        var resolvedDrmScheme = stream.drmScheme?.takeIf { it.isNotBlank() }

        if (pipePart.isNotBlank()) {
            val pairs = pipePart.split("&", "|").map { it.trim() }.filter { it.isNotBlank() }
            for (pair in pairs) {
                val eqIdx = pair.indexOf('=')
                if (eqIdx <= 0) continue
                val key = pair.substring(0, eqIdx).trim().lowercase(Locale.US)
                val rawVal = pair.substring(eqIdx + 1).trim()
                val value = runCatching {
                    if (rawVal.contains("%")) URLDecoder.decode(rawVal, "UTF-8") else rawVal
                }.getOrDefault(rawVal)
                if (value.isBlank()) continue

                when (key) {
                    "user-agent", "useragent", "http-user-agent" -> resolvedUserAgent = value
                    "referer", "referrer", "http-referrer", "http-referer" -> headers["Referer"] = value
                    "origin", "http-origin" -> headers["Origin"] = value
                    "cookie", "http-cookie" -> headers["Cookie"] = value
                    "authorization" -> headers["Authorization"] = value
                    "drmlicense", "license", "key" -> resolvedDrmLicense = value
                    "drmscheme", "scheme" -> resolvedDrmScheme = value
                    else -> {
                        if (key.startsWith("x-") || key == "host" || key == "accept") {
                            headers[pair.substring(0, eqIdx).trim()] = value
                        }
                    }
                }
            }
        }

        val finalUa = resolvedUserAgent
            ?: "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        return ParsedStreamSpec(
            cleanUrl = cleanUrl,
            userAgent = finalUa,
            headers = headers,
            drmLicense = resolvedDrmLicense,
            drmScheme = resolvedDrmScheme
        )
    }

    private fun hexToBase64Url(hexStr: String): String {
        val cleanHex = hexStr.trim().replace("-", "")
        if (cleanHex.length % 2 != 0) return cleanHex
        val bytes = ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun toClearKeyJson(licenseStr: String): ByteArray? {
        val trimmed = licenseStr.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"keys\"")) {
            return trimmed.toByteArray(Charsets.UTF_8)
        }
        val keysArr = JSONArray()
        val pairs = trimmed.split(",", ";", "&").map { it.trim() }.filter { it.contains(":") }
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                val kidHex = parts[0].trim()
                val keyHex = parts[1].trim()
                if (kidHex.isNotBlank() && keyHex.isNotBlank()) {
                    val keyObj = JSONObject().apply {
                        put("kty", "oct")
                        put("kid", hexToBase64Url(kidHex))
                        put("k", hexToBase64Url(keyHex))
                    }
                    keysArr.put(keyObj)
                }
            }
        }
        if (keysArr.length() == 0) return null
        val root = JSONObject().apply {
            put("keys", keysArr)
            put("type", "temporary")
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildDrmSessionManagerProvider(
        spec: ParsedStreamSpec,
        httpDataSourceFactory: androidx.media3.datasource.HttpDataSource.Factory
    ): DrmSessionManagerProvider {
        val license = spec.drmLicense?.trim().orEmpty()
        // Local ClearKey ("kid:key" or JSON) is decrypted in-memory by ClearKeyDashDecryptor
        // without touching native FrameworkMediaDrm / MediaCrypto (DrmHalHidl / CryptoHalHidl).
        if (license.isBlank() || (!license.startsWith("http://", ignoreCase = true) && !license.startsWith("https://", ignoreCase = true))) {
            return noOpDrmProvider
        }

        return DrmSessionManagerProvider {
            try {
                val callback = HttpMediaDrmCallback(license, httpDataSourceFactory)
                val uuid = if (spec.drmScheme?.contains("clearkey", ignoreCase = true) == true) {
                    C.CLEARKEY_UUID
                } else {
                    C.WIDEVINE_UUID
                }
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(true)
                    .build(callback)
            } catch (_: Throwable) {
                androidx.media3.exoplayer.drm.DrmSessionManager.DRM_UNSUPPORTED
            }
        }
    }

    private fun createMediaSource(stream: StreamSource, forceLowQuality: Boolean): MediaSource {
        val spec = parseStreamSpec(stream)
        val uri = Uri.parse(spec.cleanUrl)

        // Use pooled OkHttpDataSource.Factory with sharedBandwidthMeter TransferListener (matching j0xdkn.apk Lf4/a0)
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(streamHttpClient)
            .setUserAgent(spec.userAgent)

        sharedBandwidthMeter?.let { meter ->
            okHttpDataSourceFactory.setTransferListener(meter)
        }

        if (spec.headers.isNotEmpty()) {
            okHttpDataSourceFactory.setDefaultRequestProperties(spec.headers)
        }

        val hlsDataSourceFactory = DataSource.Factory {
            ParallelHighlightSegmentDataSource(
                delegate = okHttpDataSourceFactory.createDataSource(),
                userAgent = spec.userAgent,
                defaultHeaders = spec.headers
            ).apply {
                sharedBandwidthMeter?.let { addTransferListener(it) }
            }
        }

        val clearKeyContext = ClearKeyDashDecryptor.parseClearKeyLicense(spec.drmLicense.orEmpty())
        val drmProvider = if (clearKeyContext != null) {
            noOpDrmProvider
        } else {
            buildDrmSessionManagerProvider(spec, okHttpDataSourceFactory)
        }
        val lowerUrl = spec.cleanUrl.lowercase(Locale.US)
        val isDash = lowerUrl.contains(".mpd") ||
            stream.quality.contains("DASH", ignoreCase = true) ||
            (!spec.drmLicense.isNullOrBlank() && !lowerUrl.contains(".m3u8"))
        // Match j0xdkn.apk Lt7/c;.i(url) HLS detection (.m3u8, =m3u8, .php, .js, .json, /hls/, playlist)
        val isHls = !isDash && (
            lowerUrl.contains(".m3u8") ||
                lowerUrl.contains("=m3u8") ||
                lowerUrl.contains("m3u8") ||
                lowerUrl.contains("/hls/") ||
                lowerUrl.contains("playlist") ||
                lowerUrl.contains(".php") ||
                lowerUrl.contains(".js") ||
                lowerUrl.contains(".json") ||
                stream.quality.contains("HLS", ignoreCase = true)
            )

        return when {
            isDash -> {
                val dashDataSourceFactory = if (clearKeyContext != null) {
                    ClearKeyDashDecryptor.ClearKeyDecryptingDataSource.Factory(
                        upstreamFactory = okHttpDataSourceFactory,
                        cryptoContext = clearKeyContext
                    )
                } else {
                    okHttpDataSourceFactory
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()

                DashMediaSource.Factory(dashDataSourceFactory)
                    .setLoadErrorHandlingPolicy(resilientLoadErrorPolicy)
                    .setDrmSessionManagerProvider(drmProvider)
                    .createMediaSource(mediaItem)
            }
            isHls -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()

                HlsMediaSource.Factory(hlsDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(resilientLoadErrorPolicy)
                    .setDrmSessionManagerProvider(drmProvider)
                    .createMediaSource(mediaItem)
            }
            else -> {
                // Direct OkHttp progressive streaming for MP4 Highlights with default 1MB interval
                // so HTTP 206 Byte-Range requests and MOOV atom seeks start decoding immediately
                val mediaItem = MediaItem.fromUri(uri)
                ProgressiveMediaSource.Factory(okHttpDataSourceFactory)
                    .setLoadErrorHandlingPolicy(resilientLoadErrorPolicy)
                    .setDrmSessionManagerProvider(drmProvider)
                    .createMediaSource(mediaItem)
            }
        }
    }

    fun stopAndClear() {
        stopStallWatchdog()
        stopPositionTracking()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentStream.value = null
        _isPlaying.value = false
        _isBuffering.value = false
        _errorMessage.value = null
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun resume() {
        exoPlayer?.play()
    }

    fun seekRelative(offsetMs: Long) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + offsetMs).coerceIn(0L, player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun release() {
        stopStallWatchdog()
        stopPositionTracking()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _isBuffering.value = false
    }
}
