import com.music.bitchord.data.SpoolDelta;
import java.io.*;import java.nio.file.*;import java.util.*;import java.security.*;import java.util.zip.*;
class DeltaMutationHarness {
 static byte[] base=new byte[131072],target=new byte[196608];
 static String hash(byte[] b)throws Exception{StringBuilder h=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))h.append(String.format("%02x",x&255));return h.toString();}
 static byte[] fixture()throws Exception{
  new Random(414).nextBytes(base);System.arraycopy(base,32768,target,0,98304);new Random(818).nextBytes(target);
  System.arraycopy(base,32768,target,0,98304);
  ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream h=new DataOutputStream(b);h.write(SpoolDelta.MAGIC);h.writeLong(base.length);h.writeLong(target.length);h.write(MessageDigest.getInstance("SHA-256").digest(base));h.write(MessageDigest.getInstance("SHA-256").digest(target));h.writeInt(2);
  try(DataOutputStream z=new DataOutputStream(new DeflaterOutputStream(b))){z.writeByte(1);z.writeLong(32768);z.writeLong(98304);z.writeByte(2);z.writeLong(target.length-98304);z.write(target,98304,target.length-98304);}return b.toByteArray();
 }
 public static void main(String[]args)throws Exception{
  byte[] p=fixture();Path dir=Files.createTempDirectory("spool-delta-mutations");File b=dir.resolve("base").toFile(),d=dir.resolve("patch").toFile(),o=dir.resolve("output").toFile();Files.write(b.toPath(),base);Random rng=new Random(20261118);int rejected=0,identical=0,partial=0;
  try{
   for(int i=0;i<1000;i++){
    byte[] q=p.clone();int n=1+rng.nextInt(4);while(n-->0)q[rng.nextInt(q.length)]^=(byte)(1+rng.nextInt(255));Files.write(d.toPath(),q);
    try{SpoolDelta.apply(b,d,o,base.length,target.length,hash(base),hash(target),()->{});if(!Arrays.equals(target,Files.readAllBytes(o.toPath())))throw new AssertionError("Accepted altered output");identical++;}
    catch(Exception ex){rejected++;if(o.exists())throw new AssertionError("Retained rejected output");}o.delete();
   }
   Files.write(d.toPath(),p);for(int stop=4;stop<10;stop++){final int limit=stop;int[] calls={0};try{SpoolDelta.apply(b,d,o,base.length,target.length,hash(base),hash(target),()->{if(++calls[0]>=limit)throw new IOException("cancel");});throw new AssertionError("Cancellation ignored");}catch(IOException expected){if(o.exists())throw new AssertionError("Partial output survived cancel");partial++;}}
   System.out.println("mutations=1000 rejected="+rejected+" byte_identical_acceptances="+identical+" mid_prepare_cancellations="+partial+" heap_limit=64MiB host_only");
  }finally{for(File f:dir.toFile().listFiles())f.delete();dir.toFile().delete();}
 }
}
