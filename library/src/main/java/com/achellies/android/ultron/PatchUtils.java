package com.achellies.android.ultron;

import android.app.Application;
import android.util.Log;

import com.android.tools.fd.runtime.FileManager;
import com.android.tools.fd.runtime.PatchesLoader;

import dalvik.system.DexClassLoader;

import static com.android.tools.fd.runtime.BootstrapApplication.LOG_TAG;

/**
 * Created by achellies on 16/10/20.
 */

public class PatchUtils {

    public static boolean applyPatch(Application app, byte[] bytes) {
        try {
            String dexFile = FileManager.writeTempDexFile(bytes);
            if (dexFile == null) {
                Log.e(LOG_TAG, "No file to write the code to");
                return false;
            } else if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, "Reading live code from " + dexFile);
            }
            String nativeLibraryPath = FileManager.getNativeLibraryFolder().getPath();
            DexClassLoader dexClassLoader = new DexClassLoader(dexFile,
                    app.getCacheDir().getPath(), nativeLibraryPath,
                    app.getClass().getClassLoader());

            // we should transform this process with an interface/impl
            Class<?> aClass = Class.forName(
                    "com.android.tools.fd.runtime.AppPatchesLoaderImpl", true, dexClassLoader);
            try {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Got the patcher class " + aClass);
                }

                PatchesLoader loader = (PatchesLoader) aClass.newInstance();
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Got the patcher instance " + loader);
                }
                String[] getPatchedClasses = (String[]) aClass
                        .getDeclaredMethod("getPatchedClasses").invoke(loader);
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Got the list of classes ");
                    for (String getPatchedClass : getPatchedClasses) {
                        Log.v(LOG_TAG, "class " + getPatchedClass);
                    }
                }
                if (!loader.load()) {
                    return false;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Couldn't apply code changes", e);
                e.printStackTrace();
                return false;
            }
        } catch (Throwable e) {
            Log.e(LOG_TAG, "Couldn't apply code changes", e);
            return false;
        }
        return true;
    }
}
