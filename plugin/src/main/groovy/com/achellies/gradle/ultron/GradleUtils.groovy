package com.achellies.gradle.ultron

import com.android.build.gradle.*
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.variant.BaseVariantData
import org.gradle.api.DomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException

class GradleUtils {
    static
    final String sPluginMisConfiguredErrorMessage = "Plugin requires the 'android' or 'android-library' plugin to be configured.";

    /**
     * get android variant list of the project
     * @param project the compiling project
     * @return android variants
     */
    public static DomainObjectCollection<BaseVariant> getAndroidVariants(Project project) {
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            return (DomainObjectCollection<BaseVariant>) ((AppExtension) ((AppPlugin) project.getPlugins().getPlugin(AppPlugin)).extension).applicationVariants;
        } else if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            return (DomainObjectCollection<BaseVariant>) ((LibraryExtension) ((LibraryPlugin) project.getPlugins().getPlugin(LibraryPlugin)).extension).libraryVariants;
        } else {
            throw new ProjectConfigurationException(sPluginMisConfiguredErrorMessage, null)
        }
    }

    /**
     * get android variant data list of the project
     * @param project the project
     * @return android variant data list
     */
    public static List<BaseVariantData> getAndroidVariantDataList(Project project) {
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            return project.getPlugins().getPlugin(AppPlugin).variantManager.getVariantDataList();
        } else if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            return project.getPlugins().getPlugin(LibraryPlugin).variantManager.getVariantDataList();
        } else {
            throw new ProjectConfigurationException(sPluginMisConfiguredErrorMessage, null)
        }
    }

    public static BaseExtension getAndroidExtension(Project project) {
        if (project.getPlugins().hasPlugin(AppPlugin)) {
            return project.getPlugins().getPlugin(AppPlugin).extension
        } else if (project.getPlugins().hasPlugin(LibraryPlugin)) {
            return project.getPlugins().getPlugin(LibraryPlugin).extension
        } else {
            throw new ProjectConfigurationException(sPluginMisConfiguredErrorMessage, null)
        }
    }

    static def hasAndroidPlugin(Project project) {
        return project.plugins.hasPlugin(AppPlugin) || project.plugins.hasPlugin(LibraryPlugin)
    }

    static def isOfflineBuild(Project project) {
        return project.getGradle().getStartParameter().isOffline()
    }

}
