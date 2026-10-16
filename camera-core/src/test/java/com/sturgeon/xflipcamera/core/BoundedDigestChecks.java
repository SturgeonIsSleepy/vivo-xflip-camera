package com.sturgeon.xflipcamera.core;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

final class BoundedDigestChecks {
    private static int assertions;
    private BoundedDigestChecks() { }
    static int run() {
        assertions=0;
        try {
            for(int length:new int[]{0,1,3,31,16384,16385,41000}) {
                byte[] bytes=new byte[length];new Random(length).nextBytes(bytes);
                try(BoundedDigestInputStream raw=new BoundedDigestInputStream(new ByteArrayInputStream(bytes),Math.max(1,length),()->false)) {
                    BufferedInputStream parser=new BufferedInputStream(raw,16);
                    parser.mark(128);for(int i=0;i<Math.min(7,length);i++)parser.read();parser.reset();
                    parser.skip(length/2); // A parser may skip pixels and rewind buffered header bytes.
                    check(raw.finishDigest().equals(hex(MessageDigest.getInstance("SHA-256").digest(bytes))),"Exact full-file hash despite mark/reset and skip");
                    check(raw.finishDigest().equals(raw.finishDigest()),"Finished digest stable");
                    check(raw.read()==-1,"Exact-limit EOF probe accepted");
                }
            }
            ByteArrayInputStream source=new ByteArrayInputStream(new byte[10000]);
            try(BoundedDigestInputStream raw=new BoundedDigestInputStream(source,100,()->false)) {
                IOException first=failure(raw::finishDigest);
                check(first.getMessage().contains("budget"),"Over-budget input fails");
                check(source.available()==9899,"At most budget plus one byte consumed");
                check(failure(raw::finishDigest)==first,"Parser swallowing budget exception cannot produce success");
            }
            AtomicBoolean cancel=new AtomicBoolean();
            try(BoundedDigestInputStream raw=new BoundedDigestInputStream(new ByteArrayInputStream(new byte[100]),100,cancel::get)) {
                raw.read();cancel.set(true);IOException first=failure(()->raw.skip(3));
                check(first instanceof InterruptedIOException,"Cancellation checked during skip");
                cancel.set(false);check(failure(raw::finishDigest)==first,"Cancellation remains sticky");
            }
            IOException injected=new IOException("Synthetic available failure");
            try(BoundedDigestInputStream raw=new BoundedDigestInputStream(new ByteArrayInputStream(new byte[3]) {
                @Override public int available(){throw new IllegalStateException("Use explicit source below");}
            },3,()->false)) {
                // No available() is required to hash a forward-only source.
                check(raw.finishDigest().length()==64,"No available dependency");
            }
            try(BoundedDigestInputStream raw=new BoundedDigestInputStream(new InputStream() {
                @Override public int read(){return -1;}
                @Override public int available() throws IOException {throw injected;}
            },3,()->false)) {
                check(failure(raw::available)==injected,"Available I/O failure propagated");
                check(failure(raw::finishDigest)==injected,"Available failure remains sticky");
            }
            byte[] content={1,2,3,4};ByteArrayInputStream bytes=new ByteArrayInputStream(content);
            try(BoundedDigestInputStream raw=new BoundedDigestInputStream(new InputStream() {
                boolean first=true;
                @Override public int read(){return bytes.read();}
                @Override public int read(byte[] b,int o,int n){if(first){first=false;return 0;}return bytes.read(b,o,n);}
            },4,()->false)) {
                check(raw.finishDigest().equals(hex(MessageDigest.getInstance("SHA-256").digest(content))),"Zero bulk read makes bounded progress");
            }
            int[] closes={0};BoundedDigestInputStream raw=new BoundedDigestInputStream(new ByteArrayInputStream(content) {
                @Override public void close(){closes[0]++;}
            },4,()->false);
            check(!raw.markSupported(),"Digest stream itself cannot rewind");raw.close();raw.close();
            check(closes[0]==1,"Source closes once");check(failure(raw::finishDigest).getMessage().contains("closed"),"Closed stream cannot report success");
        } catch(IOException|NoSuchAlgorithmException failure){throw new AssertionError("Unexpected stream failure",failure);}
        return assertions;
    }
    private interface Action {void run() throws IOException;}
    private static IOException failure(Action action) {
        try{action.run();}catch(IOException expected){return expected;}throw new AssertionError("Expected I/O failure");
    }
    private static String hex(byte[] bytes){StringBuilder value=new StringBuilder();for(byte b:bytes)value.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return value.toString();}
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
}
