package com.achellies.gradle.ultron

import com.android.build.gradle.BaseExtension
import com.android.builder.model.BuildType
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project

class AndroidSDKUtils {

    public static String getBuildToolsPath(BaseExtension baseExtension) {
        return String.format("%s${File.separator}build-tools${File.separator}%s", baseExtension.getSdkDirectory(), baseExtension.getBuildToolsVersion())
    }

    public static int getCompileAPILevel(BaseExtension baseExtension) {
        if (baseExtension.compileSdkVersion instanceof String) {
            if (baseExtension.compileSdkVersion.startsWith("android-")) {
                def apiLevelString = baseExtension.compileSdkVersion.substring("android-".length())
                return Integer.parseInt(apiLevelString)
            } else {
                String[] splits = baseExtension.compileSdkVersion.split("-")
                return Integer.parseInt(splits[1])
            }
        } else {
            return baseExtension.compileSdkVersion
        }
    }

    public static String getCompileAndroidJarPath(BaseExtension baseExtension) {
        return String.format("%s${File.separator}platforms${File.separator}%s${File.separator}android.jar", baseExtension.getSdkDirectory(), baseExtension.getCompileSdkVersion())
    }

    public static String getSdkDirectory(BaseExtension baseExtension) {
        return baseExtension.getSdkDirectory()
    }

    public static void dx(Project project, String jarFilePath, String dexFilePath) {
        BaseExtension baseExtension = GradleUtils.getAndroidExtension(project)
        String cmdExt = Os.isFamily(Os.FAMILY_WINDOWS) ? '.bat' : ''
        String buildToolsPath = getBuildToolsPath(baseExtension);

        def stdout = new ByteArrayOutputStream()
        project.exec {
            commandLine "${buildToolsPath}${File.separator}dx${cmdExt}",
                    '-JXmx4096m',
                    '--dex',
                    "--output=${dexFilePath}",
                    jarFilePath
            standardOutput = stdout
        }
        def error = stdout.toString().trim()
        if (error) {
            println "dex error:" + error
        }
    }

    static File getProguradMappingFile(Project project, String variantDirName) {
        // check mapping file is existed
        String temp = "${project.buildDir}${File.separator}outputs${File.separator}mapping";
        String mappingFilePath = "${temp}${File.separator}${variantDirName}${File.separator}mapping.txt";

        File file = new File(mappingFilePath)

        return file
    }

    /**
     * Returns true if code minification is enabled for this build type.
     * Added to work around runProguard property being renamed to isMinifyEnabled in Android Gradle Plugin 0.14.0
     *
     * @param buildType
     * @return boolean
     */
    private boolean isMinifyEnabledCompat(BuildType buildType) {
        if (buildType.respondsTo("isMinifyEnabled")) {
            return buildType.isMinifyEnabled()
        } else {
            return buildType.runProguard
        }
    }
}
