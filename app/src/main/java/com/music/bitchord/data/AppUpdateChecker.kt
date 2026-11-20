package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import android.content.pm.PackageManager
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * BitChord ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * against the running build.
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded by Android into app-specific storage and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 */
object AppUpdateChecker {

    /**
     * True on the private prod test channel (SEPARATELY_PROVIDED_UPDATES in
     * app/build.gradle.kts): that build never offers, downloads, or installs an
     * update itself. Its updates arrive as separately provided APKs, so the
     * shared "latest release" endpoint — one endpoint and one shared asset
     * prefix, with no per-channel or per-package guard — is never consulted
     * from it, whichever entry point asks.
     */
    val externallyProvided: Boolean = BuildConfig.SEPARATELY_PROVIDED_UPDATES

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
        val sha256: String? = null,
        val sizeBytes: Long? = null,
        val deltas: List<UpdateCatalog.Delta> = emptyList(),
        val releaseId: String? = null,
        val assetId: String? = null,
    )

    sealed interface CheckState {
        data object NeverChecked : CheckState
        data object Checking : CheckState
        data object UpToDate : CheckState
        data object Available : CheckState
        data object Disabled : CheckState
        data class Failed(val message: String) : CheckState
    }
    private val _checkState = MutableStateFlow<CheckState>(CheckState.NeverChecked)
    val checkState = _checkState.asStateFlow()
    private val checkMutex = Mutex()
    private val downloadMutex = Mutex()

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/JoyalGeorge2006/spool/releases/latest"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Saved transfer records may outlive an app upgrade. Never announce an old target,
     * even for the short interval before the transfer owner reconciles it. */
    internal fun newerOffer(info: UpdateInfo?, currentVersion: String = BuildConfig.VERSION_NAME): UpdateInfo? =
        info?.takeIf { isNewer(it.version, currentVersion) }

    private fun publishAvailable(info: UpdateInfo?) {
        _available.value = newerOffer(info)
    }

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float, val bytes: Long = 0, val total: Long = 0) : DownloadState
        data class Waiting(val message: String) : DownloadState
        data class Preparing(val message: String = "Preparing update") : DownloadState
        data object Verifying : DownloadState
        data class Installing(val message: String) : DownloadState
        data class Fallback(val message: String) : DownloadState
        data object Cancelled : DownloadState
        data class Ready(val file: File, val message: String = "") : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    @Volatile private var transferInfo: UpdateInfo? = null

    suspend fun check() = withContext(Dispatchers.IO) {
        checkMutex.withLock {
            if (externallyProvided) {
                publishAvailable(null)
                _checkState.value = CheckState.Disabled
                return@withLock
            }
            _checkState.value = CheckState.Checking
            try {
                val request = Request.Builder().url(LATEST_RELEASE_URL).build()
                val body = Http.client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "Update check failed: HTTP ${response.code}" }
                    response.body?.byteStream()?.readBytesBounded(1024L*1024)?.toString(Charsets.UTF_8) ?: error("Empty update response")
                }
                val release = json.parseToJsonElement(body) as? JsonObject ?: error("Invalid release data")
                check(release["draft"]?.jsonPrimitive?.contentOrNull != "true") { "Release is not published" }
                check(release["prerelease"]?.jsonPrimitive?.contentOrNull != "true") { "Release is a preview" }
                val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: error("Missing release version")
                val latest = tag.removePrefix("v")
                check(Regex("[0-9]+(\\.[0-9]+){1,3}(-[A-Za-z0-9.]+)?").matches(latest)) { "Unsupported release version" }
                val releaseUrl = release["html_url"]?.jsonPrimitive?.contentOrNull ?: error("Missing release link")
                check(UpdateSources.release(releaseUrl)) { "Unexpected release source" }
                if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                    val assetUrl = apkAssetUrl(release) ?: error("No compatible Spool APK in this release")
                    check(UpdateSources.asset(assetUrl)) { "Unexpected download source" }
                    val asset = release["assets"]!!.jsonArray.mapNotNull { it as? JsonObject }
                        .first { it["browser_download_url"]?.jsonPrimitive?.contentOrNull == assetUrl }
                    val digest = asset["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")
                    check(digest != null && Regex("[a-fA-F0-9]{64}").matches(digest)) { "Release has no valid SHA-256 record" }
                    val size = asset["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    check(size != null && size > 0 && size <= 512L * 1024 * 1024) { "Invalid update size" }
                    var detected = UpdateInfo(latest, releaseUrl, assetUrl,
                        release["body"]?.jsonPrimitive?.contentOrNull?.take(8192), digest, size,
                        releaseId = release["id"]?.jsonPrimitive?.contentOrNull,
                        assetId = asset["id"]?.jsonPrimitive?.contentOrNull)
                    // Catalog is optional for old releases. Invalid catalog never authorizes a patch.
                    val assets = release["assets"]!!.jsonArray.mapNotNull { it as? JsonObject }
                    val catalog = assets.singleOrNull { it["name"]?.jsonPrimitive?.contentOrNull == "spool-ota-v1.json" }
                    if (catalog != null) {
                        val patches = runCatching {
                            val url = requireNotNull(catalog["browser_download_url"]?.jsonPrimitive?.contentOrNull)
                            require(UpdateSources.asset(url))
                            val bytes = requireNotNull(catalog["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull())
                            require(bytes in 1..262144)
                            val hash = catalog["digest"]?.jsonPrimitive?.contentOrNull?.removePrefix("sha256:")
                            require(UpdateSources.hash(hash))
                            val payload = Http.client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                                check(r.isSuccessful)
                                val input = requireNotNull(r.body).byteStream()
                                val data = input.readBytesBounded(bytes)
                                require(data.size.toLong()==bytes)
                                data
                            }
                            require(MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }.equals(hash,true))
                            UpdateCatalog.parse(payload.toString(Charsets.UTF_8),detected,assets,
                                BuildConfig.APPLICATION_ID,BuildConfig.VERSION_CODE.toLong(),Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toList())
                        }.getOrDefault(emptyList())
                        detected=detected.copy(deltas=patches)
                    }
                    publishAvailable(newerOffer(transferInfo) ?: detected)
                    _checkState.value = CheckState.Available
                } else {
                    publishAvailable(transferInfo)
                    _checkState.value = CheckState.UpToDate
                }
                AppSettings.setUpdatesLastChecked(System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                _checkState.value = CheckState.NeverChecked
                throw cancelled
            } catch (failure: Exception) {
                publishAvailable(transferInfo)
                _checkState.value = CheckState.Failed(failure.message ?: "Couldn't check for updates")
            }
        }
    }

    /**
     * Restore the explicit system-owned update transfer without deleting it.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        // Keep system-managed downloads and verified APKs across app restarts.
        UpdateDownloads.initialize(context)
    }

    internal fun isNewerVersion(version: String) = isNewer(version, BuildConfig.VERSION_NAME)
    internal fun transferProgress(info: UpdateInfo, fraction: Float, bytes: Long = 0, total: Long = info.sizeBytes ?: 0) {
        transferInfo = newerOffer(info)
        publishAvailable(info)
        _download.value = DownloadState.Downloading(fraction, bytes, total)
    }
    internal fun transferReady(info: UpdateInfo, file: File) {
        transferInfo = newerOffer(info)
        publishAvailable(info)
        _download.value = DownloadState.Ready(file)
    }
    internal fun transferState(info: UpdateInfo?, state: DownloadState, pinned: Boolean = true) {
        transferInfo = if(pinned)newerOffer(info) else null
        publishAvailable(info)
        _download.value = state
    }
    internal fun transferFailed(message: String) { _download.value = DownloadState.Failed(message) }
    internal fun transferIdle() { transferInfo = null; publishAvailable(null); _download.value = DownloadState.Idle }

    /**
     * One release carries the phone app and the TV app, each as a universal
     * APK plus per-ABI splits:
     *
     *     BitChord-v1.8.apk     BitChord-v1.8_arm64-v8a.apk     ...
     *     BitChordTV-v1.8.apk   BitChordTV-v1.8_arm64-v8a.apk   ...
     *
     * so this app's APKs are the ones named with its own
     * [BuildConfig.UPDATE_APK_PREFIX] — "BitChord-" never matches a TV file,
     * since the prefix ends at the hyphen. The universal build (no `_ABI`
     * suffix) is taken first, as it installs anywhere; failing that, the split
     * for this device's ABI. A release with neither leaves
     * [UpdateInfo.apkUrl] null and the UI falls back to opening the releases
     * page.
     */
    internal fun apkAssetUrl(
        release: JsonObject,
        preferredName: String = if(BuildConfig.APPLICATION_ID.endsWith(".tv"))"spool-tv.apk" else "spool.apk",
        legacyPrefix: String = BuildConfig.UPDATE_APK_PREFIX,
        abis: List<String> = Build.SUPPORTED_ABIS.toList(),
    ): String? = runCatching {
        val apks=release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }?.filter {
            it["state"]?.jsonPrimitive?.contentOrNull=="uploaded"
        }.orEmpty()
        val exact=apks.filter { it["name"]?.jsonPrimitive?.contentOrNull==preferredName }
        require(exact.size<=1) { "Ambiguous preferred APK assets" }
        if(exact.isNotEmpty())return@runCatching exact.single()["browser_download_url"]?.jsonPrimitive?.contentOrNull
        // A versioned legacy prefix must never also match a TV or Wear filename.
        val pattern=Regex(Regex.escape(legacyPrefix)+"v[0-9][A-Za-z0-9.\\-]*(?:_([A-Za-z0-9_\\-]+))?\\.apk",RegexOption.IGNORE_CASE)
        val legacy=apks.mapNotNull { asset ->
            val match=pattern.matchEntire(asset["name"]?.jsonPrimitive?.contentOrNull ?: "") ?: return@mapNotNull null
            val url=asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            match.groupValues[1] to url
        }
        val universal=legacy.filter { it.first.isEmpty() };require(universal.size<=1)
        universal.singleOrNull()?.second ?: abis.firstNotNullOfOrNull { abi ->
            val matches=legacy.filter { it.first==abi };require(matches.size<=1);matches.singleOrNull()?.second
        }
    }.getOrNull()

    /** Fail closed before handing any archive to Android, and recheck at Install. */
    internal fun verifyDownloadedApk(context: Context, file: File, info: UpdateInfo, expectedCode: Long? = null, expectedMinSdk: Int? = null, expectedAbi: String? = null) {
        check(file.isFile && file.length() == info.sizeBytes) { "Update size does not match the release record" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(info.sha256, ignoreCase = true)) { "Update checksum does not match the release record" }
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(file.path,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
            ?: error("Update is not an installable APK")
        @Suppress("DEPRECATION")
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        @Suppress("DEPRECATION")
        val installed = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES)
        @Suppress("DEPRECATION")
        fun certs(info: android.content.pm.PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            }.toSet()
        }
        check(expectedCode==null || archiveCode==expectedCode) { "APK versionCode does not match the update catalog" }
        check(expectedMinSdk==null || archive.applicationInfo?.minSdkVersion==expectedMinSdk) { "APK Android requirement does not match catalog" }
        check((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "Update requires a newer Android version" }
        java.util.zip.ZipFile(file).use { zip ->
            val abis=zip.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") && it.endsWith(".so") }
                .mapNotNull { it.split('/').getOrNull(1) }.toSet()
            check(expectedAbi==null || abis.isEmpty() || expectedAbi in abis) { "APK architecture does not match catalog" }
            check(abis.isEmpty() || Build.SUPPORTED_ABIS.any { it in abis }) { "Update does not support this device architecture" }
        }
        validateArchiveIdentity(archive.packageName, archiveCode, archive.versionName,
            certs(archive), context.packageName, BuildConfig.VERSION_CODE.toLong(), certs(installed), info.version)
    }

    /** Exact installed bytes, not a version label or callback, establish completion. */
    internal fun isInstalledTarget(context: Context, info: UpdateInfo, expectedCode: Long? = null): Boolean = runCatching {
        @Suppress("DEPRECATION")
        val installed=context.packageManager.getPackageInfo(context.packageName,0)
        @Suppress("DEPRECATION")
        val code=if(Build.VERSION.SDK_INT>=28)installed.longVersionCode else installed.versionCode.toLong()
        val file=File(context.applicationInfo.sourceDir)
        installed.packageName==context.packageName && installed.versionName==info.version &&
            (expectedCode==null || expectedCode==code) && context.applicationInfo.splitSourceDirs.isNullOrEmpty() &&
            file.isFile && file.length()==info.sizeBytes && SpoolDelta.sha256(file) { }.equals(info.sha256,true)
    }.getOrDefault(false)

    internal fun validateArchiveIdentity(
        packageName: String, versionCode: Long, versionName: String?, signers: Set<String>,
        installedPackage: String, installedCode: Long, installedSigners: Set<String>, releaseVersion: String,
    ) {
        check(packageName == installedPackage) { "Update belongs to a different application" }
        check(versionCode > installedCode) { "Update is not newer than this installation" }
        check(versionName == releaseVersion) { "APK version does not match the release" }
        check(installedSigners.isNotEmpty() && installedSigners == signers) { "Update signing key does not match this installation" }
    }

    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO + NonCancellable) {
        if (externallyProvided || !downloadMutex.tryLock()) return@withContext
        try {
            val info = _available.value ?: return@withContext
            UpdateDownloads.start(context, info)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            _download.value = DownloadState.Failed(failure.message ?: "Couldn't start update download")
        } finally { downloadMutex.unlock() }
    }

    fun cancelDownload() { UpdateDownloads.cancel() }
    suspend fun useFullDownload(context: Context) {
        val a=UpdateDownloads.snapshot()
        if(a?.phase=="fallback")UpdateDownloads.acceptFull(context)
        else available.value?.let { UpdateDownloads.start(context,it,true) }
    }
    fun resetDownload() { UpdateDownloads.cancel() }

    /**
     * Hands a downloaded APK to the system installer.
     *
     * Sideloaded apps need the user's blessing per app ("install unknown apps");
     * without it the installer intent silently does nothing on most ROMs, so
     * the user is sent to that one switch first and taps Install again after.
     * Some TV builds have no such settings screen; there the installer is
     * launched anyway and shows its own "not allowed" prompt with a way in.
     */
    suspend fun installApk(context: Context, file: File) = withContext(Dispatchers.IO) {
        if (externallyProvided) return@withContext
        UpdateInstaller.install(context)
    }

    private fun java.io.InputStream.readBytesBounded(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = read(buffer); if (n<0) break
            check(out.size().toLong()+n<=limit) { "Response exceeds size limit" }
            out.write(buffer,0,n)
        }
        return out.toByteArray()
    }

    /** A version split into its numeric dotted parts and whether it carries a "-suffix" (e.g. "-beta2"). */
    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /**
     * Numeric, dot-separated comparison — "1.10" outranks "1.9" — with one
     * extra rule: a "-betaN" build (see the debug build type's
     * `versionNameSuffix` in app/build.gradle.kts) is treated as older than a
     * plain release of the same numbers, since the beta by definition predates
     * the tag it was testing toward. Without this, a beta and the release it
     * matches compare equal and testers never get nudged onto the real build.
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
