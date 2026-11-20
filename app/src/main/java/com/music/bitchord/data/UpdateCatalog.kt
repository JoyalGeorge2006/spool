package com.music.bitchord.data

import kotlinx.serialization.json.*
import java.net.URI

internal object UpdateSources {
    private const val ROOT = "/JoyalGeorge2006/spool/releases/"
    fun release(value: String): Boolean = valid(value, ROOT) && !value.contains('?')
    fun asset(value: String): Boolean = valid(value, ROOT + "download/") && !value.contains('?')
    private fun valid(value: String, prefix: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme == "https" && u.host == "github.com" && u.port == -1 && u.userInfo == null &&
            u.fragment == null && u.rawPath.startsWith(prefix) && !u.rawPath.contains('%') &&
            !u.rawPath.contains("/../") && !u.rawPath.contains("//") && u.rawPath.length > prefix.length
    }.getOrDefault(false)
    fun hash(value: String?) = value != null && Regex("[a-fA-F0-9]{64}").matches(value)
}

/** Catalog is pinned by the official release asset digest, not independently signed. */
object UpdateCatalog {
    data class Delta(
        val baseCode: Long, val baseSize: Long, val baseSha256: String,
        val url: String, val size: Long, val sha256: String,
        val targetCode: Long, val minSdk: Int, val abi: String,
    )
    internal fun parse(text: String, full: AppUpdateChecker.UpdateInfo, assets: List<JsonObject>,
              packageName: String, code: Long, sdk: Int, abis: List<String>): List<Delta> {
        require(text.length <= 262144) { "Update catalog too large" }
        val root = Json.parseToJsonElement(text).jsonObject
        require(root["schema"]?.jsonPrimitive?.int == 1) { "Unsupported update catalog" }
        val entries = root["patches"]?.jsonArray ?: return emptyList()
        require(entries.size <= 16) { "Too many delta pairs" }
        return entries.map { value ->
            val v=value.jsonObject
            fun str(key:String)=requireNotNull(v[key]?.jsonPrimitive?.contentOrNull) { "Missing delta $key" }
            fun num(key:String)=str(key).toLong()
            require(str("format")=="spdelta-1" && str("layout")=="standalone")
            require(str("package")==packageName && str("target_version")==full.version)
            require(str("target_sha256").equals(full.sha256,true) && num("target_size")==full.sizeBytes)
            val targetCode=num("target_code");require(targetCode>code)
            val minRaw=num("min_sdk");require(minRaw in 26..10000);val min=minRaw.toInt()
            val baseCode=num("base_code");val baseSize=num("base_size");val size=num("patch_size")
            require(baseCode>0 && baseCode<targetCode && baseSize in 1..SpoolDelta.MAX_APK && size in 1..SpoolDelta.MAX_APK)
            val hash=str("patch_sha256");val baseHash=str("base_sha256");require(UpdateSources.hash(hash) && UpdateSources.hash(baseHash))
            val assetId=str("patch_asset_id")
            val asset=assets.single { it["id"]?.jsonPrimitive?.contentOrNull==assetId }
            val url=asset["browser_download_url"]!!.jsonPrimitive.content
            require(UpdateSources.asset(url) && asset["state"]?.jsonPrimitive?.content=="uploaded")
            require(asset["size"]?.jsonPrimitive?.long==size && asset["digest"]?.jsonPrimitive?.content=="sha256:$hash")
            Delta(baseCode,baseSize,baseHash,url,size,hash,targetCode,min,str("abi"))
        }.filter { it.baseCode==code && it.minSdk<=sdk && it.abi in abis && it.size < requireNotNull(full.sizeBytes) }
            .also { require(it.distinctBy { d -> d.baseSha256 }.size==it.size) { "Ambiguous delta pairs" } }
    }
}
