package com.music.bitchord.data;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/** Versioned bounded COPY/LITERAL decoder. No ZIP extraction, paths or executable code. */
public final class SpoolDelta {
    private SpoolDelta() {}
    public static final long MAX_APK = 512L * 1024 * 1024;
    public static final int MAX_OPS = 100000;
    public static final byte[] MAGIC = {'S','P','D','E','L','T','A',1};
    public interface Checkpoint { void check() throws IOException; }
    public static String sha256(File file, Checkpoint checkpoint) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        try (InputStream input = new FileInputStream(file)) {
            int n;
            while ((n = input.read(buffer)) != -1) { checkpoint.check(); digest.update(buffer, 0, n); }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
    private static byte[] hashBytes(String value) throws IOException {
        if (value == null || !value.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid hash");
        byte[] bytes = new byte[32];
        for (int i=0;i<32;i++) bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);
        return bytes;
    }
    public static void apply(File base, File patch, File output, long baseSize, long targetSize,
                             String baseHash, String targetHash, Checkpoint checkpoint) throws Exception {
        if (baseSize <= 0 || baseSize > MAX_APK || targetSize <= 0 || targetSize > MAX_APK ||
            base.length() != baseSize || patch.length() > MAX_APK ||
            output.getCanonicalFile().equals(base.getCanonicalFile()) ||
            output.getCanonicalFile().equals(patch.getCanonicalFile())) throw new IOException("Invalid delta files/sizes");
        if (!sha256(base, checkpoint).equalsIgnoreCase(baseHash)) throw new IOException("Installed APK changed");
        boolean complete=false;
        Inflater inflater = new Inflater();
        try (FileInputStream raw = new FileInputStream(patch);
             DataInputStream header = new DataInputStream(raw);
             RandomAccessFile source = new RandomAccessFile(base,"r")) {
            byte[] magic=new byte[8]; header.readFully(magic);
            if (!Arrays.equals(magic,MAGIC) || header.readLong()!=baseSize || header.readLong()!=targetSize)
                throw new IOException("Wrong delta header");
            byte[] bh=new byte[32], th=new byte[32];header.readFully(bh);header.readFully(th);
            if (!Arrays.equals(bh,hashBytes(baseHash)) || !Arrays.equals(th,hashBytes(targetHash)))
                throw new IOException("Wrong delta identity");
            int count=header.readInt();
            if(count<=0 || count>MAX_OPS) throw new IOException("Invalid operation count");
            InflaterInputStream inflated = new InflaterInputStream(raw,inflater,65536);
            DataInputStream input = new DataInputStream(inflated);
            byte[] buffer=new byte[65536]; long written=0;
            try(FileOutputStream out=new FileOutputStream(output)) {
                for(int i=0;i<count;i++) {
                    checkpoint.check();int op=input.readUnsignedByte();
                    long offset=op==1?input.readLong():0;
                    if(op!=1 && op!=2) throw new IOException("Unknown delta operation");
                    long length=input.readLong();
                    if(length<=0 || length>targetSize-written) throw new IOException("Delta output exceeds bound");
                    if(op==1) {
                        if(offset<0 || offset>baseSize || length>baseSize-offset) throw new IOException("Invalid source range");
                        source.seek(offset);
                    }
                    long remaining=length;
                    while(remaining>0) {
                        checkpoint.check();int n=(int)Math.min(buffer.length,remaining);
                        if(op==1) source.readFully(buffer,0,n);else input.readFully(buffer,0,n);
                        out.write(buffer,0,n);remaining-=n;
                    }
                    written+=length;
                }
                if(written!=targetSize || input.read()!=-1 || !inflater.finished() ||
                   inflater.getRemaining()!=0 || raw.read()!=-1) throw new IOException("Incomplete or trailing delta data");
                out.getFD().sync();
            }
            checkpoint.check();
            if(!sha256(output,checkpoint).equalsIgnoreCase(targetHash)) throw new IOException("Reconstructed APK checksum mismatch");
            // A base replacement during processing cannot silently change the selected source.
            if(base.length()!=baseSize || !sha256(base,checkpoint).equalsIgnoreCase(baseHash)) throw new IOException("Installed APK changed");
            complete=true;
        } finally { inflater.end();if(!complete) output.delete(); }
    }
}
