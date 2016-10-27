package com.achellies.gradle.ultron

import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.pipeline.TransformTask
import com.android.build.gradle.internal.transforms.ProGuardTransform
import com.android.utils.XmlUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.BuildException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.SAXException

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

import static com.android.SdkConstants.ATTR_PACKAGE
import static com.android.SdkConstants.TAG_APPLICATION

class UltronPlugin implements Plugin<Project> {
    static final String BOOTSTRAP_APPLICATION = "com.android.tools.fd.runtime.BootstrapApplication";
    static final String ANDROID_NAME_ATTRIBUTE = "android:name";

    Project project;

    @Override
    final void apply(Project project) {
        this.project = project;

        boolean registerTransform = false;
        if (project.gradle.startParameter == null || project.gradle.startParameter.taskNames == null || project.gradle.startParameter.taskNames.isEmpty()) {
            registerTransform = false;
        } else {
            def taskNames = project.gradle.startParameter.taskNames
            for (int index = 0; index < taskNames.size(); ++index) {
                def taskName = taskNames[index]
                if (taskName.endsWith("Release") || taskName.contains("Release")) {
                    registerTransform = true;
                    break;
                }
            }
        }

        applyExtension(project);

        if (registerTransform) {
            GradleUtils.getAndroidExtension(project).applicationVariants.all { BaseVariant variant ->
                // Configure ProGuard if needed
                if (variant.buildType.minifyEnabled) {
                    def variantName = variant.name.capitalize()
                    def proguardTaskName = "transformClassesAndResourcesWithProguardFor$variantName"
                    def proguard = (TransformTask) project.tasks[proguardTaskName]
                    def pt = (ProGuardTransform) proguard.getTransform()
                    configureProguard(variant, proguard, pt)
                }

                if (!variant.getOutputs().isEmpty()) {
                    def variantOutput = variant.getOutputs().get(0);
                    variantOutput.processManifest.doLast {
                        processManifest(variantOutput.processManifest.manifestOutputFile);
                    }
                }

                def dexTask = project.tasks.findByName("transformClassesWithDexFor${variant.name.capitalize()}")
            }

            // FIXME: 为了支持ProGuard,这里需要在ProGuard完成后再进行处理,所以放弃Transform
            GradleUtils.getAndroidExtension(project).registerTransform(new UltronTransform(this, project))
        }
    }

    protected void configureProguard(BaseVariant variant, TransformTask proguard, ProGuardTransform pt) {
//        pt.keep("interface com.android.tools.fd.runtime.IncrementalChange { *; }");
//        pt.keep("class * implements com.android.tools.fd.runtime.IncrementalChange { *; }");
//        pt.keep("class com.android.tools.fd.** {*;}");

        UltronExtension extension = getConfig(project);
        if (extension.applyProGuardMappingFilePath != null && !extension.applyProGuardMappingFilePath.isEmpty()) {
            def mappingFile = new File(extension.applyProGuardMappingFilePath);
            pt.applyTestedMapping(mappingFile);
        }
    }

    void applyExtension(Project project) {
        project.extensions.create(UltronExtension.sPluginExtensionName, UltronExtension);
    }

    public static UltronExtension getConfig(Project project) {
        UltronExtension config =
                project.extensions.findByType(UltronExtension.class);
        if (config == null) {
            config = new UltronExtension();
        }
        return config;
    }

    void processManifest(File manifestFile) {
        processRealApplicationName(manifestFile);
        proceedApplicationClassNameReplacement(manifestFile);
    }

    void processRealApplicationName(File manifestFile) {

        String applicationClass = null;
        String applicationId = null;
        try {
            Document document = XmlUtils.parseUtfXmlFile(manifestFile, true);
            Element root = document.getDocumentElement();
            if (root != null) {
                applicationId = root.getAttribute(ATTR_PACKAGE);
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    Node node = children.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE &&
                            node.getNodeName().equals(TAG_APPLICATION)) {
                        String applicationClass1 = null;

                        Element element = (Element) node;
                        if (element.hasAttribute(ANDROID_NAME_ATTRIBUTE)) {
                            String name = element.getAttribute(ANDROID_NAME_ATTRIBUTE);
                            assert !name.startsWith("."): name;
                            if (!name.isEmpty()) {
                                applicationClass1 = name;
                            }
                        }

                        applicationClass = applicationClass1;
                        break;
                    }
                }

                if (applicationClass == null) {
                    applicationClass = "android.app.Application";
                }
            }
        } catch (IOException | SAXException e) {
            throw new BuildException("Failed to get bootstrapping application", e);
        } finally {

        }

        getConfig(project).applicationClass = applicationClass;
        getConfig(project).applicationId = applicationId;
    }

    void proceedApplicationClassNameReplacement(File manifestFile) {
        DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder domBuilder = domFactory.newDocumentBuilder();
        Document document = domBuilder.parse(manifestFile);

        NodeList rowNodes = document.getElementsByTagName(TAG_APPLICATION);
        for (int i = 0; i < rowNodes.getLength(); i++) {
            Node rowNode = rowNodes.item(i);

            Element element = (Element) rowNode;
            if (element.hasAttribute(ANDROID_NAME_ATTRIBUTE)) {
                element.removeAttribute(ANDROID_NAME_ATTRIBUTE);
            }
            element.setAttribute(ANDROID_NAME_ATTRIBUTE, BOOTSTRAP_APPLICATION);
        }

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(new DOMSource(document),
                new StreamResult(manifestFile));

    }
}
