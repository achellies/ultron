package com.achellies.gradle.ultron

import com.android.SdkConstants
import com.android.annotations.NonNull
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.tasks.DefaultAndroidTask
import com.android.build.gradle.internal2.incremental.ByteCodeUtils
import com.android.build.gradle.internal2.incremental.IncrementalChangeVisitor
import com.android.build.gradle.internal2.incremental.IncrementalSupportVisitor
import com.android.build.gradle.internal2.incremental.IncrementalVisitor
import com.android.build.gradle.tasks.MergeManifests
import com.google.common.collect.ImmutableList
import javassist.ClassPool
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.api.Project
import org.gradle.api.Task
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

public class UltronProcessor {
    static class DirectoryInputArgs {
        @NonNull
        final File sourceDir;
        @NonNull
        final File destDir;

        public DirectoryInputArgs(@NonNull File source, @NonNull File dest) {
            super();
            this.sourceDir = source;
            this.destDir = dest;
        }
    }

    static class JarInputArgs {
        @NonNull
        final File sourceJar;
        @NonNull
        final File destJar;

        public JarInputArgs(@NonNull File source, @NonNull File dest) {
            super();
            this.sourceJar = source;
            this.destJar = dest;
        }
    }

    static class UltronArgs {
        @NonNull
        final Collection<DirectoryInputArgs> directoryInputArgses;
        @NonNull
        final Collection<JarInputArgs> jarInputArgses;

        public UltronArgs(
                @NonNull Collection<DirectoryInputArgs> directories,
                @NonNull Collection<JarInputArgs> jars) {
            super();
            this.directoryInputArgses = directories;
            this.jarInputArgses = jars;
        }
    }

    final UltronPlugin plugin;
    final Project project;
    final UltronExtension extension;
    final ImmutableList.Builder<String> generatedClasses3Names = ImmutableList.builder();
    final ClassPool classPool;
    File manifest;
    BaseVariant variant;
    PatchHashManager patchHashManager;
    File hashFile;
    ObfuscationTransformer obfuscationTransformer;
    final StringBuilder hashStringBuilder = new StringBuilder();

    public UltronProcessor(UltronPlugin plugin, Project project) {
        super();
        this.plugin = plugin;
        this.project = project;
        this.extension = UltronPlugin.getConfig(project);
        this.classPool = new ClassPool();
    }

    public void process(DefaultAndroidTask androidTask, UltronArgs ultronArgs, List<URL> referencedInputUrls, File classesEnhancedOutput) {

        def variantName = androidTask.getVariantName()

        project.getTasks().each { Task task ->
            if (task instanceof MergeManifests) {
                MergeManifests mergeManifests = (MergeManifests) task
                if (mergeManifests.variantName.contentEquals(variantName)) {
                    manifest = mergeManifests.outputFile
                    return true
                }
            }

            return false
        }

        GradleUtils.getAndroidVariants(project).each { BaseVariant it ->
            if (variantName.contentEquals(it.name)) {
                variant = it
                return
            }
        }

        // setup ClassPool
        GradleUtils.getAndroidExtension(project).bootClasspath.each { File file ->
            classPool.appendClassPath(file.absolutePath)
        }
        for (DirectoryInputArgs dirInput : ultronArgs.directoryInputArgses) {
            classPool.insertClassPath(dirInput.sourceDir.absolutePath)
        }
        for (JarInputArgs jarInput : ultronArgs.jarInputArgses) {
            classPool.insertClassPath(jarInput.sourceJar.absolutePath)
        }

        obfuscationTransformer = new ObfuscationTransformer(AndroidSDKUtils.getProguradMappingFile(project, variant.dirName));

        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            URLClassLoader urlClassLoader = new NonDelegatingUrlClassloader(referencedInputUrls);
            Thread.currentThread().setContextClassLoader(urlClassLoader);

            boolean shouldModifyBytecode = true

            boolean shouldGeneratePatch = false;

            File outputPatchFolder = new File("${project.buildDir}/outputs/${UltronExtension.sPluginExtensionName}/${variant.dirName}");
            outputPatchFolder.mkdirs();

            hashFile = new File("${outputPatchFolder}/hash.txt");
            if (hashFile.exists()) {
                hashFile.delete();
            }

            if (extension.applyClassHashFilePath != null && !extension.applyClassHashFilePath.isEmpty()) {
                def hashFile = new File(extension.applyClassHashFilePath);
                patchHashManager = new PatchHashManager(hashFile);
            }

            for (DirectoryInputArgs dirInput : ultronArgs.directoryInputArgses) {
                def dirPath = dirInput.sourceDir.absolutePath
                File file = dirInput.sourceDir;
                File dest = dirInput.destDir;

                if (shouldModifyBytecode) {
                    Collection<File> fileCollection = FileUtils.listFiles(file, null, true);
                    def iterator = fileCollection.iterator();
                    while (iterator.hasNext()) {
                        def it = iterator.next();
                        if (it.absolutePath.endsWith(SdkConstants.DOT_CLASS)) {

                            def className = it.absolutePath.substring(dirPath.length() + 1, it.absolutePath.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.');

                            if (isPackageInstantRunEnabled(className)) {
                                File inputFile = it;
                                File inputDir = dirInput.sourceDir;
                                File outputDir = dest;

                                File outputFile = IncrementalVisitor.instrumentClass(
                                        inputDir, inputFile, outputDir, IncrementalSupportVisitor.VISITOR_BUILDER);

                                def hash = DigestUtils.shaHex(Files.readAllBytes(outputFile.toPath()));
                                hashStringBuilder.append(PatchHashManager.format(className, hash));

                                boolean isChanged = (patchHashManager != null) ? !patchHashManager.isSame(className, hash) : false;

                                if (isChanged) {
                                    shouldGeneratePatch = true;
                                    outputDir = classesEnhancedOutput;
                                    outputFile = IncrementalVisitor.instrumentClass(
                                            inputDir, inputFile, outputDir, IncrementalChangeVisitor.VISITOR_BUILDER);

                                    if (outputFile != null) {
                                        generatedClasses3Names.add(className);
                                    }
                                }
                            } else {
                                String tempName = it.getAbsolutePath().substring(
                                        file.getAbsolutePath().length() + 1);

                                FileUtils.copyFile(it, new File("${dest.getAbsolutePath()}/${tempName}"));
                            }
                        }
                    }
                } else {
                    FileUtils.copyDirectory(dirInput.sourceDir, dest);
                }
            }

            for (JarInputArgs jarInput : ultronArgs.jarInputArgses) {
                File dest = jarInput.destJar;

                if (shouldModifyBytecode) {
                    def jarFile = new JarFile(jarInput.sourceJar)

                    Enumeration enumeration = jarFile.entries();

                    dest.mkdirs();
                    if (dest.exists()) {
                        dest.delete();
                    }
                    JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(dest));

                    while (enumeration.hasMoreElements()) {
                        JarEntry it = (JarEntry) enumeration.nextElement();

                        ZipEntry zipEntry = new ZipEntry(it.name);

                        InputStream inputStream = jarFile.getInputStream(it);
                        jarOutputStream.putNextEntry(zipEntry);
                        if (it.name.endsWith(SdkConstants.DOT_CLASS)) {

                            def className = it.name.substring(0, it.name.length() - SdkConstants.DOT_CLASS.length()).replaceAll('/', '.');

                            if (className.contentEquals("com.android.tools.fd.runtime.AppInfo")) {
                                jarOutputStream.write(GenerateInstantRunAppInfo.generateAppInfoClass(extension.applicationId, extension.applicationClass, extension.buildId));
                            } else {
                                if (isPackageInstantRunEnabled(className)) {
                                    def temp = File.createTempFile(it.name, SdkConstants.DOT_CLASS)

                                    File inputFile = temp;
                                    File inputDir = jarInput.sourceJar.parentFile;
                                    File outputDir = temp.parentFile;
                                    Files.copy(inputStream, temp.toPath(), StandardCopyOption.REPLACE_EXISTING);

                                    def bytes;
                                    try {

                                        File outputFile = IncrementalVisitor.instrumentClass(
                                                inputDir, inputFile, outputDir, IncrementalSupportVisitor.VISITOR_BUILDER);

                                        bytes = Files.readAllBytes(outputFile.toPath());
                                        jarOutputStream.write(bytes);
                                    } catch (Exception ignore) {
                                        bytes = IOUtils.toByteArray(inputStream);
                                    }

                                    def hash = DigestUtils.shaHex(bytes);
                                    hashStringBuilder.append(PatchHashManager.format(className, hash));

                                    boolean isChanged = (patchHashManager != null) ? !patchHashManager.isSame(className, hash) : false;

                                    if (isChanged) {
                                        shouldGeneratePatch = true;
                                        outputDir = classesEnhancedOutput;
                                        def outputFile = IncrementalVisitor.instrumentClass(
                                                inputDir, inputFile, outputDir, IncrementalChangeVisitor.VISITOR_BUILDER);

                                        if (outputFile != null) {
                                            generatedClasses3Names.add(className);
                                        }
                                    }

                                    temp.delete();
                                } else {
                                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                                }
                            }
                        } else {
                            jarOutputStream.write(IOUtils.toByteArray(inputStream));
                        }
                        jarOutputStream.closeEntry();
                    }
                    jarOutputStream.close();
                    jarFile.close();
                } else {
                    FileUtils.copyFile(jarInput.sourceJar, dest);
                }
            }

            hashFile.append(hashStringBuilder.toString());

            if (shouldGeneratePatch) {
                // otherwise, generate the patch file and add it to the list of files to process next.
                ImmutableList<String> generatedClassNames = generatedClasses3Names.build();
                if (!generatedClassNames.isEmpty()) {
                    writePatchFileContents(generatedClassNames, classesEnhancedOutput,
                            extension.buildId);

                    // 生成Jar
                    Collection<File> fileCollection = FileUtils.listFiles(classesEnhancedOutput, [
                            SdkConstants.EXT_CLASS
                    ] as String[], true);

                    File outputPatchJar = new File("${outputPatchFolder}/patch${SdkConstants.DOT_JAR}");
                    if (outputPatchJar.exists()) {
                        outputPatchJar.delete();
                    }

                    File outputPatchDex = new File("${outputPatchFolder}/patch${SdkConstants.DOT_DEX}");
                    if (outputPatchDex.exists()) {
                        outputPatchDex.delete();
                    }

                    JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outputPatchJar));

                    def iterator = fileCollection.iterator();
                    while (iterator.hasNext()) {
                        File file = iterator.next();

                        def entryName = file.getAbsolutePath().substring(
                                classesEnhancedOutput.getAbsolutePath().length() + 1);

                        def zipEntry = new ZipEntry(entryName);

                        jarOutputStream.putNextEntry(zipEntry);
                        jarOutputStream.write(Files.readAllBytes(file.toPath()));

                        jarOutputStream.closeEntry();
                    }
                    jarOutputStream.close();

                    AndroidSDKUtils.dx(project, outputPatchJar.getAbsolutePath(), outputPatchDex.getAbsolutePath());
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(currentClassLoader);
        }
    }

    boolean isPackageInstantRunEnabled(String className) {
        if (extension.supportPackageFilters == null || extension.supportPackageFilters.length == 0) {
            return true;
        }

        def ctClass = classPool.getOrNull(className);
        if (ctClass != null) {
            if (ctClass.isAnnotation() || ctClass.isPrimitive() || ctClass.isEnum() || ctClass.isInterface()) {
                return false;
            }

            // FIXME: 可以在ASM中判断是否有声明了Method,这里图省事,直接通过Javassist来判断了
            // TODO: 对于只有构造函数的Class是否也需要支持呢?
            def declaredMethods = ctClass.getDeclaredMethods();
            if (declaredMethods == null || declaredMethods.length == 0) {
                return false;
            }
        }

        def realClassName = obfuscationTransformer.getObfuscationClassName(className);

        for (def index = 0; index < extension.supportPackageFilters.length; ++index) {
            if (realClassName.startsWith(extension.supportPackageFilters[index])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Use asm to generate a concrete subclass of the AppPathLoaderImpl class.
     * It only implements one method :
     *      String[] getPatchedClasses();
     *
     * The method is supposed to return the list of classes that were patched in this iteration.
     * This will be used by the InstantRun runtime to load all patched classes and register them
     * as overrides on the original classes.2 class files.
     *
     * @param patchFileContents list of patched class names.
     * @param outputDir output directory where to generate the .class file in.
     */
    private static void writePatchFileContents(
            @NonNull ImmutableList<String> patchFileContents,
            @NonNull File outputDir, long buildId) {

        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC + Opcodes.ACC_SUPER,
                IncrementalVisitor.APP_PATCHES_LOADER_IMPL, null,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL, null);

        // Add the build ID to force the patch file to be repackaged.
        cw.visitField(Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC + Opcodes.ACC_FINAL,
                "BUILD_ID", "J", null, buildId);


        mv = cw.visitMethod(Opcodes.ACC_PUBLIC, ByteCodeUtils.CONSTRUCTOR, "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                IncrementalVisitor.ABSTRACT_PATCHES_LOADER_IMPL,
                ByteCodeUtils.CONSTRUCTOR, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();


        mv = cw.visitMethod(Opcodes.ACC_PUBLIC,
                "getPatchedClasses", "()[Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitIntInsn(Opcodes.BIPUSH, patchFileContents.size());
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/String");
        for (int index = 0; index < patchFileContents.size(); index++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitIntInsn(Opcodes.BIPUSH, index);
            mv.visitLdcInsn(patchFileContents.get(index));
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(4, 1);
        mv.visitEnd();

        cw.visitEnd();

        byte[] classBytes = cw.toByteArray();
        File outputFile = new File(outputDir, IncrementalVisitor.APP_PATCHES_LOADER_IMPL + SdkConstants.DOT_CLASS);
        try {
            com.google.common.io.Files.createParentDirs(outputFile);
            com.google.common.io.Files.write(classBytes, outputFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class NonDelegatingUrlClassloader extends URLClassLoader {

        public NonDelegatingUrlClassloader(@NonNull List<URL> urls) {
            super(urls.toArray(new URL[urls.size()]), (ClassLoader) null);
        }

        @Override
        public URL getResource(String name) {
            // Never delegate to bootstrap classes.
            return findResource(name);
        }
    }
}
