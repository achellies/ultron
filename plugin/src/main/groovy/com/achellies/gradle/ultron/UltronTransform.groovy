package com.achellies.gradle.ultron

import com.android.annotations.NonNull
import com.android.build.api.transform.*
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.LoggerWrapper
import com.android.build.gradle.internal.SdkHandler
import com.android.build.gradle.internal.pipeline.ExtendedContentType
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.variant.BaseVariantData
import com.android.builder.core.AndroidBuilder
import com.android.builder.core.BootClasspathBuilder
import com.android.repository.api.ProgressIndicator
import com.android.sdklib.IAndroidTarget
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.sdklib.repository.LoggerProgressIndicatorWrapper
import com.android.utils.ILogger
import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.api.Project

public class UltronTransform extends Transform {
    private static final ILogger LOGGER = LoggerWrapper.getLogger(UltronTransform.class);
    final UltronPlugin plugin;
    final Project project;
    final UltronExtension extension;
    final UltronProcessor ultronProcessor;
    BaseVariantData variantData;

    public UltronTransform(UltronPlugin plugin, Project project) {
        super();
        this.plugin = plugin;
        this.project = project;
        this.extension = UltronPlugin.getConfig(project);
        ultronProcessor = new UltronProcessor(plugin, project);
    }

    @Override
    public String getName() {
        return UltronExtension.sPluginExtensionName;
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return ImmutableSet.<QualifiedContent.ContentType> of(
                QualifiedContent.DefaultContentType.CLASSES,
                ExtendedContentType.CLASSES_ENHANCED);
    }

    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @NonNull
    @Override
    public Set<QualifiedContent.Scope> getReferencedScopes() {
        return Sets.immutableEnumSet(QualifiedContent.Scope.EXTERNAL_LIBRARIES,
                QualifiedContent.Scope.PROJECT_LOCAL_DEPS,
                QualifiedContent.Scope.SUB_PROJECTS_LOCAL_DEPS,
                QualifiedContent.Scope.PROVIDED_ONLY);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        assert (transformInvocation.context instanceof TransformTask)

        TransformTask transformTask = (TransformTask) transformInvocation.context

        def variantName = transformTask.getVariantName();

        BaseVariant variant;
        GradleUtils.getAndroidVariants(project).each { BaseVariant it ->
            if (variantName.contentEquals(it.name)) {
                variant = it
                return
            }
        }

        GradleUtils.getAndroidVariantDataList(project).each { BaseVariantData it ->
            if (it.name.toLowerCase().contentEquals(variant.name.toLowerCase())) {
                variantData = it
            }
        }

        // first get all referenced input to construct a class loader capable of loading those
        // classes. This is useful for ASM as it needs to load classes
        List<URL> referencedInputUrls = getAllClassesLocations(
                transformInvocation.getInputs(), transformInvocation.getReferencedInputs());

        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        File classesEnhancedOutput = outputProvider.getContentLocation("enhanced",
                ImmutableSet.<QualifiedContent.ContentType> of(ExtendedContentType.CLASSES_ENHANCED),
                getScopes(), Format.DIRECTORY);
        outputProvider.deleteAll()

        ArrayList<UltronProcessor.DirectoryInputArgs> directoryInputArgses = new ArrayList<>();
        ArrayList<UltronProcessor.JarInputArgs> jarInputArgses = new ArrayList<>();
        for (TransformInput input : transformInvocation.getInputs()) {
            for (DirectoryInput dirInput : input.directoryInputs) {
                File source = dirInput.getFile();
                File dest = transformInvocation.getOutputProvider().getContentLocation(dirInput.name, dirInput.contentTypes, dirInput.scopes, Format.DIRECTORY);

                directoryInputArgses.add(new UltronProcessor.DirectoryInputArgs(source, dest));
            }

            for (JarInput jarInput : input.jarInputs) {
                File source = jarInput.getFile();
                File dest = transformInvocation.getOutputProvider().getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR);

                jarInputArgses.add(new UltronProcessor.JarInputArgs(source, dest));
            }
        }

        UltronProcessor.UltronArgs ultronArgs = new UltronProcessor.UltronArgs(directoryInputArgses, jarInputArgses);

        ultronProcessor.process(transformTask, ultronArgs, referencedInputUrls, classesEnhancedOutput);
    }

    /**
     * Calculate a list of {@link URL} that represent all the directories containing classes
     * either directly belonging to this project or referencing it.
     *
     * @param inputs the project's inputs
     * @param referencedInputs the project's referenced inputs
     * @return a {@link List} or {@link URL} for all the locations.
     * @throws MalformedURLException if once the locatio
     */
    @NonNull
    private List<URL> getAllClassesLocations(
            @NonNull Collection<TransformInput> inputs,
            @NonNull Collection<TransformInput> referencedInputs) throws MalformedURLException {

        List<URL> referencedInputUrls = new ArrayList<>();

        // add the bootstrap classpath for jars like android.jar
        for (File file : getInstantRunBootClasspath()) {
            referencedInputUrls.add(file.toURI().toURL());
        }
//        for (File file : GradleUtils.getAndroidExtension(project).bootClasspath) {
//            referencedInputUrls.add(file.toURI().toURL());
//        }

        // now add the project dependencies.
        for (TransformInput referencedInput : referencedInputs) {
            addAllClassLocations(referencedInput, referencedInputUrls);
        }

        // and finally add input folders.
        for (TransformInput input : inputs) {
            addAllClassLocations(input, referencedInputUrls);
        }
        return referencedInputUrls;
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

    /**
     * Calls the sdklib machinery to construct the {@link IAndroidTarget} for the given hash string.
     *
     * @return appropriate {@link IAndroidTarget} or null if the matching platform package is not
     *         installed.
     */
    private static IAndroidTarget getAndroidTarget(
            @NonNull SdkHandler sdkHandler,
            @NonNull String targetHash) {
        File sdkLocation = sdkHandler.getSdkFolder();
        ProgressIndicator progressIndicator = new LoggerProgressIndicatorWrapper(LOGGER);
        IAndroidTarget target = AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
        if (target != null) {
            return target;
        }
        // reset the cached AndroidSdkHandler, next time a target is looked up,
        // this will force the re-parsing of the SDK.
        AndroidSdkHandler.resetInstance(sdkLocation);

        // and let's try immediately, it's possible the platform was installed since the SDK
        // handler was initialized in the this VM, since we reset the instance just above, it's
        // possible we find it.
        return AndroidSdkHandler.getInstance(sdkLocation)
                .getAndroidTargetManager(progressIndicator)
                .getTargetFromHashString(targetHash, progressIndicator);
    }

    private static void addAllClassLocations(TransformInput transformInput, List<URL> into)
            throws MalformedURLException {

        for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
            into.add(directoryInput.getFile().toURI().toURL());
        }
        for (JarInput jarInput : transformInput.getJarInputs()) {
            into.add(jarInput.getFile().toURI().toURL());
        }
    }
}
