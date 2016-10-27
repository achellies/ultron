package com.achellies.gradle.ultron

import com.android.SdkConstants
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.tasks.DefaultAndroidTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BootClasspathBuilder
import com.android.builder.model.AndroidProject
import com.android.sdklib.IAndroidTarget
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

public class UltronTask extends DefaultAndroidTask {
    public UltronPlugin plugin;
    public BaseVariant variant;
    public DefaultTask dexTask;
    BaseVariantData variantData;

    public UltronTask() {
        super();
    }

    @TaskAction
    public void process() {
        Project project = plugin.project;
        UltronProcessor ultronProcessor = new UltronProcessor(plugin, project);

        GradleUtils.getAndroidVariantDataList(project).each { BaseVariantData it ->
            if (it.name.toLowerCase().contentEquals(variant.name.toLowerCase())) {
                variantData = it
            }
        }

        variantName = variant.name;

        List<URL> referencedInputUrls = new ArrayList<>();

        // add the bootstrap classpath for jars like android.jar
        for (File file : getInstantRunBootClasspath()) {
            referencedInputUrls.add(file.toURI().toURL());
        }

        File classesEnhancedOutput = new File("${project.buildDir}/${AndroidProject.FD_INTERMEDIATES}/transforms/${UltronExtension.sPluginExtensionName}/${variant.dirName}/enhanced/");
        if (classesEnhancedOutput.exists()) {
            FileUtils.cleanDirectory(classesEnhancedOutput);
        }

        ArrayList<UltronProcessor.DirectoryInputArgs> directoryInputArgses = new ArrayList<>();
        ArrayList<UltronProcessor.JarInputArgs> jarInputArgses = new ArrayList<>();

        for (File inputFile : dexTask.inputs.files.files) {
            def extensions = [SdkConstants.EXT_JAR] as String[]
            if (inputFile.exists()) {
                if (inputFile.isDirectory()) {
                    Collection<File> jars = FileUtils.listFiles(inputFile, extensions, true);
                    for (File it : jars) {
                        File source = it;
                        File dest = new File("${it.name}.tmp", it.parentFile);

                        referencedInputUrls.add(source.toURI().toURL());

                        jarInputArgses.add(new UltronProcessor.JarInputArgs(source, dest));
                    }
                    if (jars.isEmpty()) {
                        File tempDir = new File("${inputFile.parentFile.absolutePath}/${System.currentTimeMillis()}/");

                        FileUtils.copyDirectory(inputFile, tempDir);

                        UltronProcessor.DirectoryInputArgs directoryInputArgs = new UltronProcessor.DirectoryInputArgs(tempDir, inputFile);
                        directoryInputArgses.add(directoryInputArgs);
                    }
                } else if (inputFile.name.endsWith(SdkConstants.DOT_JAR)) {
                    File source = inputFile;
                    File dest = new File("${inputFile.name}.tmp", inputFile.parentFile);

                    referencedInputUrls.add(source.toURI().toURL());

                    jarInputArgses.add(new UltronProcessor.JarInputArgs(source, dest));
                }
            }
        }

        UltronProcessor.UltronArgs ultronArgs = new UltronProcessor.UltronArgs(directoryInputArgses, jarInputArgses);

        ultronProcessor.process(this, ultronArgs, referencedInputUrls, classesEnhancedOutput);

        for (UltronProcessor.JarInputArgs jarInput : jarInputArgses) {
            if (jarInput.destJar.exists()) {
                jarInput.sourceJar.delete();

                jarInput.destJar.renameTo(jarInput.sourceJar);
            }
        }

        for (UltronProcessor.DirectoryInputArgs directoryInputArgs : directoryInputArgses) {
            if (directoryInputArgs.sourceDir.exists()) {
                FileUtils.deleteDirectory(directoryInputArgs.sourceDir);
            }
        }
    }

    public ImmutableList<File> getInstantRunBootClasspath() {
        SdkHandler sdkHandler = variantData.taskManager.sdkHandler;
        AndroidBuilder androidBuilder = variantData.taskManager.androidBuilder;
        IAndroidTarget androidBuilderTarget = androidBuilder.getTarget();

        Preconditions.checkState(
                androidBuilderTarget != null,
                "AndroidBuilder target not initialized.");

        File annotationsJar = sdkHandler.getSdkLoader().getSdkInfo(null).getAnnotationsJar();

        return BootClasspathBuilder.computeFullBootClasspath(
                androidBuilderTarget, annotationsJar);
    }
}

