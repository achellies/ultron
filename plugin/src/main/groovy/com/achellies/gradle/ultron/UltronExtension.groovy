package com.achellies.gradle.ultron;

public class UltronExtension {
    static final String sPluginExtensionName = "ultron";

    String applicationId;
    String applicationClass;
    long buildId = 123321L;

    String[] supportPackageFilters;
    String applyClassHashFilePath;
    String applyProGuardMappingFilePath;
}

