package com.example.player

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import com.example.data.model.StreamSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

@OptIn(UnstableApi::class)
class ExoPlayerManager(private val context: Context) {

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

    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
    private var fallbackCandidates: List<StreamSource> = emptyList()
    private var failedStreamUrls = mutableSetOf<String>()
    private var currentForceLowQuality: Boolean = false

    fun setFallbackCandidates(candidates: List<StreamSource>, forceLowQuality: Boolean = false) {
        fallbackCandidates = candidates.filter { it.isEnabled && it.url.isNotBlank() }
        currentForceLowQuality = forceLowQuality
    }

    fun getPlayer(): ExoPlayer {
        if (exoPlayer == null) {
            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    15_000, // min buffer 15s
                    50_000, // max buffer 50s
                    1_500,  // playback start buffer 1.5s (fast start)
                    3_000   // re-buffer 3s
                )
                .build()

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
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
                                }
                                Player.STATE_READY -> {
                                    _isBuffering.value = false
                                    _errorMessage.value = null
                                    _duration.value = duration.coerceAtLeast(0L)
                                    startPositionTracking()
                                }
                                Player.STATE_ENDED -> {
                                    _isBuffering.value = false
                                    _isPlaying.value = false
                                    stopPositionTracking()
                                }
                                Player.STATE_IDLE -> {
                                    _isBuffering.value = false
                                    stopPositionTracking()
                                }
                            }
                        }

                        override fun onIsPlayingChanged(playing: Boolean) {
                            _isPlaying.value = playing
                            if (playing) {
                                startPositionTracking()
                            } else {
                                stopPositionTracking()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                                exoPlayer?.let { p ->
                                    p.seekToDefaultPosition()
                                    p.prepare()
                                    p.playWhenReady = true
                                }
                                return
                            }
                            _isBuffering.value = false
                            _isPlaying.value = false
                            stopPositionTracking()
                            val failedUrl = _currentStream.value?.url
                            if (!failedUrl.isNullOrBlank()) {
                                failedStreamUrls.add(failedUrl)
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

    fun playStream(stream: StreamSource, forceLowQuality: Boolean = false) {
        playStreamInternal(stream, forceLowQuality, resetFailedSet = true)
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
        currentForceLowQuality = forceLowQuality
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
        val cleanUrl = raw.substringBefore("|").trim()
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
        httpDataSourceFactory: DefaultHttpDataSource.Factory
    ): DrmSessionManagerProvider? {
        val license = spec.drmLicense?.trim().orEmpty()
        if (license.isBlank()) return null

        return DrmSessionManagerProvider {
            if (license.startsWith("http://", ignoreCase = true) || license.startsWith("https://", ignoreCase = true)) {
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
            } else {
                val clearKeyBytes = toClearKeyJson(license) ?: license.toByteArray(Charsets.UTF_8)
                val callback = LocalMediaDrmCallback(clearKeyBytes)
                DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(callback)
            }
        }
    }

    private fun createMediaSource(stream: StreamSource, forceLowQuality: Boolean): MediaSource {
        val spec = parseStreamSpec(stream)
        val uri = Uri.parse(spec.cleanUrl)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(spec.userAgent)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(12_000)
            .setAllowCrossProtocolRedirects(true)

        if (spec.headers.isNotEmpty()) {
            httpDataSourceFactory.setDefaultRequestProperties(spec.headers)
        }

        // Prevent DefaultHttpDataSource.skipFully(DefaultHttpDataSource.java:744) EOFException
        // when live IPTV servers return HTTP 200 OK instead of 206 Partial Content on chunk retries
        val liveSafeDataSourceFactory = ResolvingDataSource.Factory(httpDataSourceFactory) { dataSpec ->
            if (dataSpec.position > 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) {
                dataSpec.buildUpon().setPosition(0L).build()
            } else {
                dataSpec
            }
        }

        val drmProvider = buildDrmSessionManagerProvider(spec, httpDataSourceFactory)
        val lowerUrl = spec.cleanUrl.lowercase(Locale.US)
        val isDash = lowerUrl.contains(".mpd") ||
            stream.quality.contains("DASH", ignoreCase = true) ||
            (!spec.drmLicense.isNullOrBlank() && !lowerUrl.contains(".m3u8"))
        val isHls = !isDash && (
            lowerUrl.contains(".m3u8") ||
                lowerUrl.contains("m3u8") ||
                stream.quality.contains("HLS", ignoreCase = true)
            )

        return when {
            isDash -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .build()
                    )
                    .build()

                val factory = DashMediaSource.Factory(liveSafeDataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(2))
                if (drmProvider != null) {
                    factory.setDrmSessionManagerProvider(drmProvider)
                }
                factory.createMediaSource(mediaItem)
            }
            isHls -> {
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f)
                            .build()
                    )
                    .build()

                val factory = HlsMediaSource.Factory(liveSafeDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))
                if (drmProvider != null) {
                    factory.setDrmSessionManagerProvider(drmProvider)
                }
                factory.createMediaSource(mediaItem)
            }
            else -> {
                val mediaItem = MediaItem.fromUri(uri)
                val factory = ProgressiveMediaSource.Factory(liveSafeDataSourceFactory)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(1))
                if (drmProvider != null) {
                    factory.setDrmSessionManagerProvider(drmProvider)
                }
                factory.createMediaSource(mediaItem)
            }
        }
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
        stopPositionTracking()
        exoPlayer?.release()
        exoPlayer = null
        _isPlaying.value = false
        _isBuffering.value = false
    }
}
