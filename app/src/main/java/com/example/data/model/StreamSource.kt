package com.example.data.model

import java.util.UUID

data class StreamSource(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // e.g., "FANCODE - AQ", "TAPMAD - AQ", "T SPORTS HD", "SD STREAM"
    val quality: String = "FHD", // FHD, HD, SD, 1080p, 720p
    val url: String, // .m3u8, .mpd, .mp4, or url|referer=...&origin=...&user-agent=...
    val userAgent: String? = null,
    val referer: String? = null,
    val origin: String? = null,
    val cookie: String? = null,
    val drmLicense: String? = null, // ClearKey "kid:key" or license URL
    val drmScheme: String? = null, // "clearkey" or "widevine"
    val isDefault: Boolean = false,
    val isWorking: Boolean? = null, // Result of stream validator check
    val isEnabled: Boolean = true, // Admin control: whether this stream is enabled for public users
    val priorityOrder: Int = 0, // Lower number = higher priority in stream chooser
    val isBackup: Boolean = false // Whether this stream is a backup server
)
