package com.achellies.android.ultron.sample;

import android.app.Application;
import android.content.res.AssetManager;
import android.os.Debug;

import com.achellies.android.ultron.PatchUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by achellies on 16/10/19.
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

//        Debug.waitForDebugger();

        AssetManager assetManager = this.getAssets();
        InputStream in = null;
        try {
            in = assetManager.open("patch.dex");
            PatchUtils.applyPatch(this, toByteArray(in));
        } catch (IOException ignore) {
            ignore.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {
                }
                in = null;
            }
        }
    }

    public static byte[] toByteArray(InputStream input)
            throws IOException
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }

    public static int copy(InputStream input, OutputStream output)
            throws IOException
    {
        long count = copyLarge(input, output);
        if (count > 2147483647L) {
            return -1;
        }
        return (int)count;
    }

    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException
    {
        byte[] buffer = new byte[4096];
        long count = 0L;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
