package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure-Kotlin ISO BMFF (fMP4 / MPEG-DASH) Common Encryption (`cenc` AES-128-CTR) ClearKey decryptor.
 *
 * Decrypts ClearKey-protected `.mpd` manifests, initialization segments (`init.mp4`),
 * and media segments (`.mp4` / `.m4s`) directly in memory without invoking Android's
 * native `FrameworkMediaDrm` / `MediaCrypto` HAL (`DrmHalHidl` / `CryptoHalHidl`).
 */
@OptIn(UnstableApi::class)
object ClearKeyDashDecryptor {

    private val CONTENT_PROTECTION_BLOCK_REGEX =
        Regex("""<ContentProtection\b[^>]*>[\s\S]*?</ContentProtection>""", RegexOption.IGNORE_CASE)
    private val CONTENT_PROTECTION_SELF_CLOSING_REGEX =
        Regex("""<ContentProtection\b[^>]*/>""", RegexOption.IGNORE_CASE)

    data class TrackCryptoConfig(
        val ivSize: Int,
        val kidHex: String,
        val keyBytes: ByteArray
    )

    class StreamCryptoContext(
        val keysByKidHex: Map<String, ByteArray>,
        val defaultKeyBytes: ByteArray
    ) {
        val trackConfigs = ConcurrentHashMap<Int, TrackCryptoConfig>()
        @Volatile
        var lastTrackConfig: TrackCryptoConfig = TrackCryptoConfig(
            ivSize = 8,
            kidHex = keysByKidHex.keys.firstOrNull().orEmpty(),
            keyBytes = defaultKeyBytes
        )
    }

    fun parseClearKeyLicense(licenseStr: String): StreamCryptoContext? {
        val trimmed = licenseStr.trim()
        if (trimmed.isBlank() || trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            return null
        }

        val map = LinkedHashMap<String, ByteArray>()
        if (trimmed.startsWith("{") && trimmed.contains("\"keys\"")) {
            runCatching {
                val root = JSONObject(trimmed)
                val keysArr = root.optJSONArray("keys")
                if (keysArr != null) {
                    for (i in 0 until keysArr.length()) {
                        val kObj = keysArr.optJSONObject(i) ?: continue
                        val kidB64 = kObj.optString("kid", "")
                        val kB64 = kObj.optString("k", "")
                        val kidBytes = decodeBase64Url(kidB64)
                        val keyBytes = decodeBase64Url(kB64)
                        if (kidBytes.size == 16 && keyBytes.size == 16) {
                            map[bytesToHex(kidBytes)] = keyBytes
                        }
                    }
                }
            }
        } else {
            val pairs = trimmed.split(",", ";", "&").map { it.trim() }.filter { it.contains(":") }
            for (pair in pairs) {
                val parts = pair.split(":")
                if (parts.size == 2) {
                    val kidHex = parts[0].trim().replace("-", "").lowercase(Locale.US)
                    val keyHex = parts[1].trim().replace("-", "").lowercase(Locale.US)
                    val keyBytes = hexToBytes(keyHex)
                    if (kidHex.length == 32 && keyBytes != null && keyBytes.size == 16) {
                        map[kidHex] = keyBytes
                    }
                }
            }
        }

        val firstKey = map.values.firstOrNull() ?: return null
        return StreamCryptoContext(keysByKidHex = map, defaultKeyBytes = firstKey)
    }

    fun cleanMpdManifestBytes(rawBytes: ByteArray): ByteArray {
        val xml = String(rawBytes, Charsets.UTF_8)
        val cleaned = xml
            .replace(CONTENT_PROTECTION_BLOCK_REGEX, "")
            .replace(CONTENT_PROTECTION_SELF_CLOSING_REGEX, "")
        return cleaned.toByteArray(Charsets.UTF_8)
    }

    fun transformMp4SegmentInPlace(data: ByteArray, ctx: StreamCryptoContext): ByteArray {
        var pos = 0
        val limit = data.size
        while (pos + 8 <= limit) {
            val boxSize = readBoxSize(data, pos, limit)
            if (boxSize < 8 || pos + boxSize > limit) break
            val boxType = readFourCc(data, pos + 4)
            when (boxType) {
                "moov" -> processMoovBox(data, pos + 8, pos + boxSize, ctx)
                "moof" -> {
                    val moofStart = pos
                    val moofEnd = pos + boxSize
                    val mdatPayloadStart = findFollowingMdatPayloadStart(data, moofEnd, limit)
                    processMoofBox(data, moofStart, moofEnd, mdatPayloadStart, ctx)
                }
            }
            pos += boxSize
        }
        return data
    }

    private fun processMoovBox(data: ByteArray, start: Int, end: Int, ctx: StreamCryptoContext) {
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            when (readFourCc(data, pos + 4)) {
                "pssh" -> writeFourCc(data, pos + 4, "free")
                "trak" -> processTrakBox(data, pos + 8, pos + size, ctx)
            }
            pos += size
        }
    }

    private fun processTrakBox(data: ByteArray, start: Int, end: Int, ctx: StreamCryptoContext) {
        var trackId = -1
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            when (readFourCc(data, pos + 4)) {
                "tkhd" -> {
                    val version = data[pos + 8].toInt() and 0xFF
                    val idOffset = if (version == 1) pos + 28 else pos + 20
                    if (idOffset + 4 <= pos + size) {
                        trackId = readInt32(data, idOffset)
                    }
                }
                "mdia" -> processContainerForStsd(data, pos + 8, pos + size, trackId, ctx)
            }
            pos += size
        }
    }

    private fun processContainerForStsd(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        ctx: StreamCryptoContext
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            when (readFourCc(data, pos + 4)) {
                "minf", "stbl" -> processContainerForStsd(data, pos + 8, pos + size, trackId, ctx)
                "stsd" -> {
                    if (pos + 16 < pos + size) {
                        processStsdEntries(data, pos + 16, pos + size, trackId, ctx)
                    }
                }
                "sbgp", "sgpd" -> writeFourCc(data, pos + 4, "free")
            }
            pos += size
        }
    }

    private fun processStsdEntries(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        ctx: StreamCryptoContext
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            val entryType = readFourCc(data, pos + 4)
            val childrenOffset = when (entryType) {
                "encv", "avc1", "avc3", "hev1", "hvc1", "vp09", "av01" -> pos + 86
                "enca", "mp4a", "ac-3", "ec-3", "Opus" -> pos + 36
                "enct", "stpp", "wvtt" -> pos + 16
                else -> -1
            }
            if (childrenOffset in (pos + 8)..(pos + size)) {
                processSampleEntryChildren(data, pos, childrenOffset, pos + size, trackId, ctx)
            }
            pos += size
        }
    }

    private fun processSampleEntryChildren(
        data: ByteArray,
        sampleEntryPos: Int,
        childrenStart: Int,
        sampleEntryEnd: Int,
        trackId: Int,
        ctx: StreamCryptoContext
    ) {
        var pos = childrenStart
        while (pos + 8 <= sampleEntryEnd) {
            val size = readBoxSize(data, pos, sampleEntryEnd)
            if (size < 8 || pos + size > sampleEntryEnd) break
            if (readFourCc(data, pos + 4) == "sinf") {
                val origFormat = parseSinfAndRegisterTrack(data, pos + 8, pos + size, trackId, ctx)
                if (origFormat != null && origFormat.length == 4) {
                    writeFourCc(data, sampleEntryPos + 4, origFormat)
                }
                writeFourCc(data, pos + 4, "free")
            }
            pos += size
        }
    }

    private fun parseSinfAndRegisterTrack(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        ctx: StreamCryptoContext
    ): String? {
        var origFormat: String? = null
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            when (readFourCc(data, pos + 4)) {
                "frma" -> {
                    if (pos + 12 <= pos + size) {
                        origFormat = readFourCc(data, pos + 8)
                    }
                }
                "schi" -> {
                    parseSchiForTenc(data, pos + 8, pos + size, trackId, ctx)
                }
            }
            pos += size
        }
        return origFormat
    }

    private fun parseSchiForTenc(
        data: ByteArray,
        start: Int,
        end: Int,
        trackId: Int,
        ctx: StreamCryptoContext
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size = readBoxSize(data, pos, end)
            if (size < 8 || pos + size > end) break
            if (readFourCc(data, pos + 4) == "tenc" && pos + 32 <= pos + size) {
                val version = data[pos + 8].toInt() and 0xFF
                val ivSizeOffset = if (version == 0) pos + 15 else pos + 15
                val rawIvSize = data[ivSizeOffset].toInt() and 0xFF
                val ivSize = if (rawIvSize == 16) 16 else 8
                val kidBytes = data.copyOfRange(ivSizeOffset + 1, ivSizeOffset + 17)
                val kidHex = bytesToHex(kidBytes)
                val keyBytes = ctx.keysByKidHex[kidHex] ?: ctx.defaultKeyBytes
                val cfg = TrackCryptoConfig(ivSize = ivSize, kidHex = kidHex, keyBytes = keyBytes)
                ctx.lastTrackConfig = cfg
                if (trackId > 0) {
                    ctx.trackConfigs[trackId] = cfg
                }
            }
            pos += size
        }
    }

    private fun findFollowingMdatPayloadStart(data: ByteArray, fromPos: Int, limit: Int): Int {
        var pos = fromPos
        while (pos + 8 <= limit) {
            val size = readBoxSize(data, pos, limit)
            if (size < 8 || pos + size > limit) break
            if (readFourCc(data, pos + 4) == "mdat") {
                val rawSize = readInt32(data, pos)
                return if (rawSize == 1) pos + 16 else pos + 8
            }
            pos += size
        }
        return -1
    }

    private fun processMoofBox(
        data: ByteArray,
        moofStart: Int,
        moofEnd: Int,
        mdatPayloadStart: Int,
        ctx: StreamCryptoContext
    ) {
        var pos = moofStart + 8
        while (pos + 8 <= moofEnd) {
            val size = readBoxSize(data, pos, moofEnd)
            if (size < 8 || pos + size > moofEnd) break
            when (readFourCc(data, pos + 4)) {
                "pssh" -> writeFourCc(data, pos + 4, "free")
                "traf" -> processTrafBox(data, moofStart, pos + 8, pos + size, mdatPayloadStart, ctx)
            }
            pos += size
        }
    }

    private fun processTrafBox(
        data: ByteArray,
        moofStart: Int,
        trafStart: Int,
        trafEnd: Int,
        mdatPayloadStart: Int,
        ctx: StreamCryptoContext
    ) {
        var trackId = -1
        var defaultSampleSize = 0
        var dataOffset = -1
        var trunSampleSizes = IntArray(0)
        var sencPos = -1
        var sencSize = 0

        var pos = trafStart
        while (pos + 8 <= trafEnd) {
            val size = readBoxSize(data, pos, trafEnd)
            if (size < 8 || pos + size > trafEnd) break
            when (readFourCc(data, pos + 4)) {
                "tfhd" -> {
                    if (pos + 16 <= pos + size) {
                        val tfhdFlags = readInt24(data, pos + 9)
                        trackId = readInt32(data, pos + 12)
                        var p = pos + 16
                        if (tfhdFlags and 0x000001 != 0) p += 8
                        if (tfhdFlags and 0x000002 != 0) p += 4
                        if (tfhdFlags and 0x000008 != 0) p += 4
                        if (tfhdFlags and 0x000010 != 0 && p + 4 <= pos + size) {
                            defaultSampleSize = readInt32(data, p)
                        }
                    }
                }
                "trun" -> {
                    if (pos + 16 <= pos + size) {
                        val trunFlags = readInt24(data, pos + 9)
                        val sampleCount = readInt32(data, pos + 12).coerceIn(0, 100_000)
                        var p = pos + 16
                        if (trunFlags and 0x000001 != 0 && p + 4 <= pos + size) {
                            dataOffset = readInt32(data, p)
                            p += 4
                        }
                        if (trunFlags and 0x000004 != 0) p += 4
                        val sizes = IntArray(sampleCount)
                        for (i in 0 until sampleCount) {
                            if (trunFlags and 0x000100 != 0) p += 4
                            val sSize = if (trunFlags and 0x000200 != 0 && p + 4 <= pos + size) {
                                val v = readInt32(data, p)
                                p += 4
                                v
                            } else {
                                defaultSampleSize
                            }
                            if (trunFlags and 0x000400 != 0) p += 4
                            if (trunFlags and 0x000800 != 0) p += 4
                            sizes[i] = sSize
                        }
                        trunSampleSizes = sizes
                    }
                }
                "senc" -> {
                    sencPos = pos
                    sencSize = size
                }
                "saio", "saiz", "sbgp", "sgpd", "uuid" -> {
                    writeFourCc(data, pos + 4, "free")
                }
            }
            pos += size
        }

        if (sencPos >= 0 && sencSize >= 16) {
            val trackCfg = (if (trackId > 0) ctx.trackConfigs[trackId] else null) ?: ctx.lastTrackConfig
            val sampleDataStart = when {
                dataOffset > 0 && moofStart + dataOffset < data.size -> moofStart + dataOffset
                mdatPayloadStart > 0 -> mdatPayloadStart
                else -> -1
            }
            if (sampleDataStart in 0 until data.size) {
                decryptSamplesWithSenc(
                    data = data,
                    sencPos = sencPos,
                    sencEnd = sencPos + sencSize,
                    mdatStart = sampleDataStart,
                    trunSampleSizes = trunSampleSizes,
                    defaultSampleSize = defaultSampleSize,
                    trackCfg = trackCfg
                )
            }
            writeFourCc(data, sencPos + 4, "free")
        }
    }

    private fun decryptSamplesWithSenc(
        data: ByteArray,
        sencPos: Int,
        sencEnd: Int,
        mdatStart: Int,
        trunSampleSizes: IntArray,
        defaultSampleSize: Int,
        trackCfg: TrackCryptoConfig
    ) {
        val sencFlags = readInt24(data, sencPos + 9)
        var cursor = sencPos + 12
        var overrideIvSize = -1
        if (sencFlags and 0x01 != 0 && cursor + 20 <= sencEnd) {
            overrideIvSize = data[cursor + 3].toInt() and 0xFF
            cursor += 20
        }
        if (cursor + 4 > sencEnd) return
        val sampleCount = readInt32(data, cursor).coerceIn(0, 100_000)
        cursor += 4

        val hasSubsamples = (sencFlags and 0x02) != 0
        val ivSize = resolveExactIvSize(
            data = data,
            samplesStart = cursor,
            sencEnd = sencEnd,
            sampleCount = sampleCount,
            hasSubsamples = hasSubsamples,
            preferredIvSize = if (overrideIvSize == 8 || overrideIvSize == 16) overrideIvSize else trackCfg.ivSize
        )

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        val keySpec = SecretKeySpec(trackCfg.keyBytes, "AES")
        val iv16 = ByteArray(16)
        var mdatCursor = mdatStart

        for (i in 0 until sampleCount) {
            if (cursor + ivSize > sencEnd) break
            iv16.fill(0)
            System.arraycopy(data, cursor, iv16, 0, ivSize)
            cursor += ivSize

            if (hasSubsamples) {
                if (cursor + 2 > sencEnd) break
                val subCount = readUInt16(data, cursor)
                cursor += 2
                if (cursor + subCount * 6 > sencEnd) break

                if (subCount == 1) {
                    val clearBytes = readUInt16(data, cursor)
                    val encBytes = readInt32(data, cursor + 2)
                    cursor += 6
                    mdatCursor += clearBytes
                    if (encBytes > 0 && mdatCursor + encBytes <= data.size) {
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv16))
                        val dec = cipher.doFinal(data, mdatCursor, encBytes)
                        System.arraycopy(dec, 0, data, mdatCursor, dec.size)
                    }
                    mdatCursor += encBytes
                } else {
                    val subCursorStart = cursor
                    var totalEncBytes = 0
                    for (s in 0 until subCount) {
                        val encBytes = readInt32(data, cursor + 2)
                        if (encBytes > 0) totalEncBytes += encBytes
                        cursor += 6
                    }
                    if (totalEncBytes > 0) {
                        val encBuffer = ByteArray(totalEncBytes)
                        var encWritePos = 0
                        var scanMdat = mdatCursor
                        var scanSenc = subCursorStart
                        for (s in 0 until subCount) {
                            val clearBytes = readUInt16(data, scanSenc)
                            val encBytes = readInt32(data, scanSenc + 2)
                            scanSenc += 6
                            scanMdat += clearBytes
                            if (encBytes > 0 && scanMdat + encBytes <= data.size) {
                                System.arraycopy(data, scanMdat, encBuffer, encWritePos, encBytes)
                                encWritePos += encBytes
                            }
                            scanMdat += encBytes
                        }
                        cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv16))
                        val decBuffer = cipher.doFinal(encBuffer, 0, encWritePos)
                        var decReadPos = 0
                        scanSenc = subCursorStart
                        for (s in 0 until subCount) {
                            val clearBytes = readUInt16(data, scanSenc)
                            val encBytes = readInt32(data, scanSenc + 2)
                            scanSenc += 6
                            mdatCursor += clearBytes
                            if (encBytes > 0 && mdatCursor + encBytes <= data.size) {
                                System.arraycopy(decBuffer, decReadPos, data, mdatCursor, encBytes)
                                decReadPos += encBytes
                            }
                            mdatCursor += encBytes
                        }
                    } else {
                        var scanSenc = subCursorStart
                        for (s in 0 until subCount) {
                            val clearBytes = readUInt16(data, scanSenc)
                            val encBytes = readInt32(data, scanSenc + 2)
                            scanSenc += 6
                            mdatCursor += clearBytes + encBytes
                        }
                    }
                }
            } else {
                val sampleSize = if (i < trunSampleSizes.size) trunSampleSizes[i] else defaultSampleSize
                if (sampleSize > 0 && mdatCursor + sampleSize <= data.size) {
                    cipher.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv16))
                    val dec = cipher.doFinal(data, mdatCursor, sampleSize)
                    System.arraycopy(dec, 0, data, mdatCursor, dec.size)
                }
                mdatCursor += sampleSize.coerceAtLeast(0)
            }
        }
    }

    private fun resolveExactIvSize(
        data: ByteArray,
        samplesStart: Int,
        sencEnd: Int,
        sampleCount: Int,
        hasSubsamples: Boolean,
        preferredIvSize: Int
    ): Int {
        if (sampleCount <= 0) return preferredIvSize
        if (!hasSubsamples) {
            val exact = (sencEnd - samplesStart) / sampleCount
            if (exact == 8 || exact == 16) return exact
            return preferredIvSize
        }
        val candidates = if (preferredIvSize == 16) intArrayOf(16, 8) else intArrayOf(8, 16)
        for (candidate in candidates) {
            var p = samplesStart
            var valid = true
            for (i in 0 until sampleCount) {
                p += candidate
                if (p + 2 > sencEnd) {
                    valid = false
                    break
                }
                val subCount = readUInt16(data, p)
                p += 2 + subCount * 6
                if (p > sencEnd) {
                    valid = false
                    break
                }
            }
            if (valid && p == sencEnd) return candidate
        }
        return preferredIvSize
    }

    private fun readBoxSize(data: ByteArray, pos: Int, limit: Int): Int {
        if (pos + 8 > limit) return -1
        val size32 = readInt32(data, pos)
        if (size32 == 1) {
            if (pos + 16 > limit) return -1
            val high = readInt32(data, pos + 8)
            val low = readInt32(data, pos + 12)
            return if (high == 0 && low >= 16) low else -1
        }
        if (size32 == 0) {
            return limit - pos
        }
        return size32
    }

    private fun readFourCc(data: ByteArray, pos: Int): String {
        return String(data, pos, 4, Charsets.ISO_8859_1)
    }

    private fun writeFourCc(data: ByteArray, pos: Int, fourCc: String) {
        for (i in 0 until 4) {
            data[pos + i] = fourCc[i].code.toByte()
        }
    }

    private fun readUInt16(data: ByteArray, pos: Int): Int {
        return ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)
    }

    private fun readInt24(data: ByteArray, pos: Int): Int {
        return ((data[pos].toInt() and 0xFF) shl 16) or
            ((data[pos + 1].toInt() and 0xFF) shl 8) or
            (data[pos + 2].toInt() and 0xFF)
    }

    private fun readInt32(data: ByteArray, pos: Int): Int {
        return ((data[pos].toInt() and 0xFF) shl 24) or
            ((data[pos + 1].toInt() and 0xFF) shl 16) or
            ((data[pos + 2].toInt() and 0xFF) shl 8) or
            (data[pos + 3].toInt() and 0xFF)
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return runCatching {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun decodeBase64Url(str: String): ByteArray {
        return runCatching {
            android.util.Base64.decode(str, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        }.getOrDefault(ByteArray(0))
    }

    class ClearKeyDecryptingDataSource(
        private val upstream: DataSource,
        private val cryptoContext: StreamCryptoContext
    ) : DataSource {

        private var transformedBytes: ByteArray? = null
        private var readOffset: Int = 0
        private var currentUri: Uri? = null
        private var cachedHeaders: Map<String, List<String>> = emptyMap()

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            upstream.open(dataSpec)
            currentUri = upstream.uri ?: dataSpec.uri
            cachedHeaders = upstream.responseHeaders

            val out = ByteArrayOutputStream(64 * 1024)
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = upstream.read(buf, 0, buf.size)
                if (r == C.RESULT_END_OF_INPUT || r < 0) break
                if (r > 0) out.write(buf, 0, r)
            }
            runCatching { upstream.close() }

            val raw = out.toByteArray()
            val processed = when {
                isXmlManifest(raw, currentUri) -> cleanMpdManifestBytes(raw)
                isMp4Segment(raw) -> transformMp4SegmentInPlace(raw, cryptoContext)
                else -> raw
            }

            transformedBytes = processed
            readOffset = 0
            return processed.size.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val data = transformedBytes ?: return C.RESULT_END_OF_INPUT
            val remaining = data.size - readOffset
            if (remaining <= 0) return C.RESULT_END_OF_INPUT
            val toCopy = minOf(length, remaining)
            System.arraycopy(data, readOffset, buffer, offset, toCopy)
            readOffset += toCopy
            return toCopy
        }

        override fun getUri(): Uri? = currentUri

        override fun getResponseHeaders(): Map<String, List<String>> = cachedHeaders

        override fun close() {
            transformedBytes = null
            readOffset = 0
            runCatching { upstream.close() }
        }

        private fun isXmlManifest(raw: ByteArray, uri: Uri?): Boolean {
            val path = uri?.path?.lowercase(Locale.US).orEmpty()
            if (path.endsWith(".mpd")) return true
            if (raw.size < 16) return false
            val prefix = String(raw, 0, minOf(raw.size, 128), Charsets.UTF_8).trimStart()
            return prefix.startsWith("<?xml", ignoreCase = true) || prefix.startsWith("<MPD", ignoreCase = true)
        }

        private fun isMp4Segment(raw: ByteArray): Boolean {
            if (raw.size < 8) return false
            val boxSize = readBoxSize(raw, 0, raw.size)
            if (boxSize < 8 || boxSize > raw.size) return false
            val boxType = readFourCc(raw, 4)
            return boxType == "ftyp" ||
                boxType == "styp" ||
                boxType == "moov" ||
                boxType == "moof" ||
                boxType == "sidx" ||
                boxType == "emsg" ||
                boxType == "prft" ||
                boxType == "free" ||
                boxType == "skip" ||
                boxType == "pdin" ||
                boxType == "mdat" ||
                boxType == "uuid"
        }

        class Factory(
            private val upstreamFactory: DataSource.Factory,
            private val cryptoContext: StreamCryptoContext
        ) : DataSource.Factory {
            override fun createDataSource(): DataSource {
                return ClearKeyDecryptingDataSource(upstreamFactory.createDataSource(), cryptoContext)
            }
        }
    }
}
