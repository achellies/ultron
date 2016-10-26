package com.achellies.gradle.ultron

import com.android.annotations.NonNull
import com.google.common.collect.Maps
import proguard.classfile.util.ClassUtil
import proguard.obfuscate.MappingProcessor
import proguard.obfuscate.MappingReader
import proguard.util.ListUtil

public class ObfuscationTransformer implements MappingProcessor {
    private static class ObfuscationInfo {
        String className
        HashMap<String, String> fieldMap = Maps.newHashMap()
        HashMap<String, String> methodMap = Maps.newHashMap()
    }

    File mappingFile

    boolean verbose = false

    HashMap<String, ObfuscationInfo> mappingInfo = Maps.newHashMap()

    String processedClassName

    public ObfuscationTransformer(@NonNull File mappingFile) {
        super()
        this.mappingFile = mappingFile

        if (mappingFile.exists() && mappingFile.canRead()) {
            MappingReader reader = new MappingReader(mappingFile)
            reader.pump(this)
        }
    }

    public void dump() {
        mappingInfo.each { Map.Entry<String, ObfuscationInfo> entry ->
            def className = ClassUtil.externalClassName(entry.key)

            println "${ClassUtil.externalClassName(entry.value.className)} -> ${className}:"
            entry.value.fieldMap.each { Map.Entry<String, String> fieldEntry ->
                String[] temp = fieldEntry.key.split("-")
                String internalType = temp[0]
                String externalType = ClassUtil.externalType(internalType)

                println "\t${externalType} ${fieldEntry.value} -> ${temp[1]}"
            }

            entry.value.methodMap.each { Map.Entry<String, String> methodEntry ->
                String[] temp = methodEntry.key.split("-")

                println "\t${methodEntry.value} -> ${temp[0]}"
            }
        }
    }

    /**
     * 返回class混淆前对应的名称
     * @param className 混淆后的Class名称
     * @return 返回className对应混淆前的名称
     */
    public String transformClassMapping(@NonNull String className) {
        def name = ClassUtil.internalClassName(className)

        if (mappingInfo.containsKey(name)) {
            return ClassUtil.externalClassName(mappingInfo.get(name).className)
        }

        return ClassUtil.externalClassName(className)
    }

    /**
     *
     * @param className 混淆前的class名称
     * @return 返回该Class对应的混淆后的名称
     */
    public String getObfuscationClassName(@NonNull String className) {
        Iterator<Map.Entry<String, ObfuscationInfo>> iterator = mappingInfo.entrySet().iterator()
        while (iterator.hasNext()) {
            Map.Entry<String, ObfuscationInfo> entry = iterator.next()

            if (ClassUtil.externalClassName(entry.value.className).contentEquals(ClassUtil.externalClassName(className))) {
                return ClassUtil.externalClassName(entry.key)
            }
        }

        return className
    }

    /**

     * @param className 混淆后的Class名称
     * @param fieldName 混淆后的field名称
     * @param fieldDescriptor field的混淆后的描述
     * @return 返回field混淆前的名称
     */
    public String transformFieldMapping(
            @NonNull String className, @NonNull String fieldName, @NonNull String fieldDescriptor) {
        def name = ClassUtil.internalClassName(className)

        if (mappingInfo.containsKey(name)) {
            if (mappingInfo.get(name).fieldMap.containsKey("${fieldDescriptor}-${fieldName}")) {
                return mappingInfo.get(name).fieldMap.get("${fieldDescriptor}-${fieldName}")
            }
        }

        return fieldName
    }

    /**
     *
     * @param className 混淆后的class名称
     * @param methodName 混淆后的method名称
     * @param methodDescriptor method的混淆后的签名描述
     * @return 返回method对应混淆前的名称
     */
    public String transformMethodMapping(
            @NonNull String className,
            @NonNull String methodName, @NonNull String methodDescriptor) {
        def name = ClassUtil.internalClassName(className)

        if (mappingInfo.containsKey(name)) {
            if (mappingInfo.get(name).methodMap.containsKey("${methodName}-${methodDescriptor}")) {
                return mappingInfo.get(name).methodMap.get("${methodName}-${methodDescriptor}")
            }
        }

        return methodName
    }

    // Implementations for MappingProcessor.
    public boolean processClassMapping(String className,
                                       String newClassName) {
        if (verbose) {
            println "****** processClassMapping(${className} ${newClassName})"
        }
        // Find the class.
        String name = ClassUtil.internalClassName(className);

        String newName = ClassUtil.internalClassName(newClassName);

        ObfuscationInfo info = mappingInfo.get(newName)
        if (info == null) {
            info = new ObfuscationInfo()
            mappingInfo.put(newName, info)
            info.className = name
        } else {
            info.className = name
        }
        processedClassName = newName

        // The class members have to be kept as well.
        return true;
    }

    public void processFieldMapping(String className,
                                    String fieldType,
                                    String fieldName,
                                    String newClassName,
                                    String newFieldName) {
        if (className.equals(newClassName)) {
            if (verbose) {
                println "****** processFieldMapping(${className} ${fieldName} ${newClassName} ${newFieldName})"
            }
            // Find the field.
            String name = fieldName;
            String descriptor = ClassUtil.internalType(fieldType);

            ObfuscationInfo info = mappingInfo.get(processedClassName)

            info.fieldMap.put("${descriptor}-${newFieldName}", name)
        }
    }

    public void processMethodMapping(String className,
                                     int firstLineNumber,
                                     int lastLineNumber,
                                     String methodReturnType,
                                     String methodName,
                                     String methodArguments,
                                     String newClassName,
                                     int newFirstLineNumber,
                                     int newLastLineNumber,
                                     String newMethodName) {
        if (className.equals(newClassName)) {
            if (verbose) {
                println "****** processMethodMapping(${className} ${methodName} ${newClassName} ${newMethodName})"
            }
            // Find the method.
            String descriptor = ClassUtil.internalMethodDescriptor(methodReturnType,
                    ListUtil.commaSeparatedList(methodArguments));

            ObfuscationInfo info = mappingInfo.get(processedClassName)

            info.methodMap.put("${newMethodName}-${descriptor}", "${ClassUtil.externalMethodReturnType(descriptor)} ${methodName}(${ClassUtil.externalMethodArguments(descriptor)})")
        }
    }
}
