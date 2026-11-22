package com.music.bitchord

import com.music.bitchord.data.SpoolDelta
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.DeflaterOutputStream

class SpoolDeltaTest {
    private fun hash(b:ByteArray)=MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
    private fun patch(base:ByteArray,target:ByteArray,ops:Int=2,body:(DataOutputStream)->Unit):ByteArray {
        val output=ByteArrayOutputStream();val h=DataOutputStream(output)
        h.write(SpoolDelta.MAGIC);h.writeLong(base.size.toLong());h.writeLong(target.size.toLong())
        h.write(MessageDigest.getInstance("SHA-256").digest(base));h.write(MessageDigest.getInstance("SHA-256").digest(target));h.writeInt(ops)
        DeflaterOutputStream(output).use { z -> body(DataOutputStream(z)) }
        return output.toByteArray()
    }
    private fun apply(p:ByteArray,base:ByteArray=byteArrayOf(1,2,3),target:ByteArray=byteArrayOf(2,3,4),cancel:Boolean=false):ByteArray {
        val dir=Files.createTempDirectory("delta-test").toFile()
        try {
            val b=File(dir,"base").apply { writeBytes(base) };val d=File(dir,"patch").apply { writeBytes(p) };val out=File(dir,"out")
            try {
                SpoolDelta.apply(b,d,out,base.size.toLong(),target.size.toLong(),hash(base),hash(target)) { if(cancel)throw IOException("cancel") }
            } catch(e:Exception) { assertFalse("Partial output retained",out.exists());throw e }
            return out.readBytes()
        } finally { dir.deleteRecursively() }
    }
    private fun valid():ByteArray=patch(byteArrayOf(1,2,3),byteArrayOf(2,3,4)) { x ->
        x.writeByte(1);x.writeLong(1);x.writeLong(2);x.writeByte(2);x.writeLong(1);x.writeByte(4)
    }
    @Test fun exactCopyLiteralReconstruction() { assertArrayEquals(byteArrayOf(2,3,4),apply(valid())) }
    @Test fun rejectsEveryTruncation() { val p=valid();for(n in 0 until p.size)assertTrue("cut $n",runCatching { apply(p.copyOf(n)) }.isFailure) }
    @Test fun rejectsTrailingData() { assertTrue(runCatching { apply(valid()+byteArrayOf(0)) }.isFailure) }
    @Test fun cancellationRemovesPartialOutput() { assertTrue(runCatching { apply(valid(),cancel=true) }.isFailure) }
    @Test fun rejectsUnknownOpcode() { assertTrue(runCatching { apply(patch(byteArrayOf(1,2,3),byteArrayOf(2,3,4),1) { it.writeByte(9);it.writeLong(3) }) }.isFailure) }
    @Test fun rejectsOverflowRange() { assertTrue(runCatching { apply(patch(byteArrayOf(1,2,3),byteArrayOf(2,3,4),1) { it.writeByte(1);it.writeLong(Long.MAX_VALUE);it.writeLong(3) }) }.isFailure) }
    @Test fun rejectsZeroNegativeAndOverlongLiteral() { for(n in listOf(0L,-1L,Long.MAX_VALUE,4L))assertTrue(runCatching { apply(patch(byteArrayOf(1,2,3),byteArrayOf(2,3,4),1) { it.writeByte(2);it.writeLong(n) }) }.isFailure) }
    @Test fun rejectsZeroAndExcessiveCounts() { for(n in listOf(0,-1,100001))assertTrue(runCatching { apply(patch(byteArrayOf(1,2,3),byteArrayOf(2,3,4),n) {}) }.isFailure) }
    @Test fun rejectsWrongBase() { assertTrue(runCatching { apply(valid(),base=byteArrayOf(8,8,8)) }.isFailure) }
    @Test fun rejectsWrongTarget() { assertTrue(runCatching { apply(valid(),target=byteArrayOf(8,8,8)) }.isFailure) }
    @Test fun rejectsCorruptMagic() { val p=valid();p[0]=0;assertTrue(runCatching { apply(p) }.isFailure) }
}
