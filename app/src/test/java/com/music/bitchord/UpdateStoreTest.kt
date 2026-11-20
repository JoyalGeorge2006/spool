package com.music.bitchord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.music.bitchord.data.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UpdateStoreTest {
 private val context=ApplicationProvider.getApplicationContext<Context>()
 private val info=AppUpdateChecker.UpdateInfo("99.0.0","https://github.com/JoyalGeorge2006/spool/releases/tag/v99.0.0","https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/spool-v99.0.0_arm64-v8a.apk","notes","a".repeat(64),10000)
 @Before fun setup(){File(context.noBackupFilesDir,"spool-update-v1.json").delete();File(context.noBackupFilesDir,"spool-update-v1.json.bak").delete()}
 @After fun cleanup()=setup()
 @Test fun roundTripEveryPhaseAndPinnedDelta() {
  val d=UpdateCatalog.Delta(1,123,"b".repeat(64),"https://github.com/JoyalGeorge2006/spool/releases/download/v99.0.0/a.spdelta",100,"c".repeat(64),999,26,"arm64-v8a")
  for(phase in listOf("preparing","queued","waiting","downloading","verifying","applying","ready","failed","fallback","cancelled","permission","staging","confirmation","installing","installed")) {
   val a=UpdateAttempt(UUID.randomUUID().toString(),info,phase,12,d,"message",9,"operation",true)
   UpdateStore.write(context,a);val got=UpdateStore.read(context)!!
   assertEquals(a,got)
  }
 }
 @Test fun rejectsPathTraversalToken() {
  val a=UpdateAttempt(UUID.randomUUID().toString(),info)
  UpdateStore.write(context,a)
  val f=File(context.noBackupFilesDir,"spool-update-v1.json");f.writeText(f.readText().replace(a.token,"../../outside"))
  assertTrue(runCatching{UpdateStore.read(context)}.isFailure)
 }
 @Test fun rejectsUnexpectedSources() {
  val a=UpdateAttempt(UUID.randomUUID().toString(),info.copy(apkUrl="https://github.com.attacker.invalid/JoyalGeorge2006/spool/releases/download/a.apk"))
  UpdateStore.write(context,a);assertTrue(runCatching{UpdateStore.read(context)}.isFailure)
 }
 @Test fun rejectsOversizedRecord() {
  File(context.noBackupFilesDir,"spool-update-v1.json").writeText("x".repeat(65537));assertTrue(runCatching{UpdateStore.read(context)}.isFailure)
 }
 @Test fun strictUrlAuthorityAndPath() {
  assertTrue(UpdateSources.asset(info.apkUrl!!))
  for(url in listOf("http://github.com/JoyalGeorge2006/spool/releases/download/a","https://github.com.evil.test/JoyalGeorge2006/spool/releases/download/a","https://evil@github.com/JoyalGeorge2006/spool/releases/download/a","https://github.com:443/JoyalGeorge2006/spool/releases/download/a","https://github.com/JoyalGeorge2006/spool/releases/download/%2e%2e/a","https://github.com/JoyalGeorge2006/spool/releases/download/a#x"))assertFalse(url,UpdateSources.asset(url))
 }
}
