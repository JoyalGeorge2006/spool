package com.music.bitchord.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Atomic single-process record. No credentials, arbitrary paths or intent blobs. */
internal data class UpdateAttempt(
    val token: String,
    val info: AppUpdateChecker.UpdateInfo,
    val phase: String = "preparing",
    val id: Long = -1,
    val delta: UpdateCatalog.Delta? = null,
    val message: String = "",
    val session: Int = -1,
    val installToken: String = "",
    val legacy: Boolean = false,
    val autoInstallPending: Boolean = false,
    val permissionHandoffLaunched: Boolean = false,
) {
    fun directory(c: Context) = File(c.noBackupFilesDir, "ota/$token")
    fun transfer(c: Context) = File(requireNotNull(c.getExternalFilesDir(null)), "updates/$token/download.bin")
    fun candidate(c: Context) = File(directory(c), "candidate.apk")
    fun temporary(c: Context) = File(directory(c), "candidate.tmp")
    val transferSize get() = delta?.size ?: requireNotNull(info.sizeBytes)
    val transferHash get() = delta?.sha256 ?: requireNotNull(info.sha256)
    val transferUrl get() = delta?.url ?: requireNotNull(info.apkUrl)
}

internal object UpdateStore {
    private fun atomic(c: Context)=AtomicFile(File(c.noBackupFilesDir,"spool-update-v1.json"))
    fun write(c: Context, a: UpdateAttempt) {
        val i=a.info
        val j=JSONObject().put("schema",1).put("token",a.token).put("phase",a.phase).put("id",a.id)
            .put("version",i.version).put("apk",i.apkUrl).put("release",i.releaseUrl)
            .put("sha",i.sha256).put("size",i.sizeBytes).put("notes",i.notes?.take(8192) ?: JSONObject.NULL)
            .put("releaseId",i.releaseId ?: "").put("assetId",i.assetId ?: "")
            .put("permissionHandoffLaunched",a.permissionHandoffLaunched).put("autoInstallPending",a.autoInstallPending).put("legacy",a.legacy).put("message",a.message.take(1024)).put("session",a.session).put("installToken",a.installToken)
        a.delta?.let { d -> j.put("delta",JSONObject().put("baseCode",d.baseCode).put("baseSize",d.baseSize)
            .put("baseHash",d.baseSha256).put("url",d.url).put("size",d.size).put("sha",d.sha256)
            .put("code",d.targetCode).put("min",d.minSdk).put("abi",d.abi)) }
        val bytes=j.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=65536) { "Update record exceeds bound" }
        val file=atomic(c);val out=file.startWrite()
        try { out.write(bytes);file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out);throw e }
    }
    fun clear(c: Context) { atomic(c).delete() }
    fun read(c: Context): UpdateAttempt? {
        val f=atomic(c)
        if(!f.baseFile.exists() && !File(f.baseFile.path+".bak").exists())return null
        val bytes=f.openRead().use { input ->
            val out=java.io.ByteArrayOutputStream();val b=ByteArray(8192)
            while(true){val n=input.read(b);if(n<0)break;require(out.size()+n<=65536);out.write(b,0,n)}
            out.toByteArray()
        }
        val j=JSONObject(bytes.toString(Charsets.UTF_8));require(j.getInt("schema")==1)
        val token=j.getString("token");require(UUID.fromString(token).toString()==token)
        val i=AppUpdateChecker.UpdateInfo(j.getString("version"),j.getString("release"),j.getString("apk"),if(j.isNull("notes"))null else j.getString("notes"),j.getString("sha"),j.getLong("size"),releaseId=j.optString("releaseId").ifBlank { null },assetId=j.optString("assetId").ifBlank { null })
        require(UpdateSources.release(i.releaseUrl) && UpdateSources.asset(requireNotNull(i.apkUrl)))
        require(UpdateSources.hash(i.sha256) && requireNotNull(i.sizeBytes) in 1..SpoolDelta.MAX_APK)
        val d=j.optJSONObject("delta")?.let {
            UpdateCatalog.Delta(it.getLong("baseCode"),it.getLong("baseSize"),it.getString("baseHash"),it.getString("url"),it.getLong("size"),it.getString("sha"),it.getLong("code"),it.getInt("min"),it.getString("abi")).also { v ->
                require(UpdateSources.asset(v.url) && UpdateSources.hash(v.sha256) && UpdateSources.hash(v.baseSha256))
                require(v.baseSize in 1..SpoolDelta.MAX_APK && v.size in 1..SpoolDelta.MAX_APK && v.targetCode>v.baseCode)
            }
        }
        val phase=j.getString("phase");require(phase in setOf("preparing","queued","downloading","waiting","verifying","applying","ready","failed","fallback","cancelled","permission","staging","confirmation","installing","installed"))
        return UpdateAttempt(token,i,phase,j.optLong("id",-1),d,j.optString("message"),j.optInt("session",-1),j.optString("installToken"),j.optBoolean("legacy",false),j.optBoolean("autoInstallPending",false),j.optBoolean("permissionHandoffLaunched",false))
    }
}
