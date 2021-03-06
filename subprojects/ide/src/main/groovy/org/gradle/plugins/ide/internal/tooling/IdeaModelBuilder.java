/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.Transformer;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.internal.jvm.Jvm;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.*;
import org.gradle.plugins.ide.internal.tooling.idea.*;
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaRuntime;
import org.gradle.plugins.ide.internal.tooling.java.DefaultJavaSourceSettings;
import org.gradle.tooling.internal.gradle.DefaultGradleModuleVersion;
import org.gradle.tooling.internal.gradle.DefaultGradleProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.io.File;
import java.util.*;

import static org.gradle.util.CollectionUtils.*;

public class IdeaModelBuilder implements ToolingModelBuilder {
    private final GradleProjectBuilder gradleProjectBuilder;

    private boolean offlineDependencyResolution;

    public IdeaModelBuilder(GradleProjectBuilder gradleProjectBuilder) {
        this.gradleProjectBuilder = gradleProjectBuilder;
    }

    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.IdeaProject");
    }

    public DefaultIdeaProject buildAll(String modelName, Project project) {
        Project root = project.getRootProject();
        applyIdeaPlugin(root);
        DefaultGradleProject<?> rootGradleProject = gradleProjectBuilder.buildAll(project);
        return build(root, rootGradleProject);
    }

    private void applyIdeaPlugin(Project root) {
        Set<Project> allProjects = root.getAllprojects();
        for (Project p : allProjects) {
            p.getPluginManager().apply(IdeaPlugin.class);
        }
        root.getPlugins().getPlugin(IdeaPlugin.class).makeSureModuleNamesAreUnique();
    }

    private DefaultIdeaProject build(Project project, DefaultGradleProject rootGradleProject) {
        IdeaModel ideaModel = project.getPlugins().getPlugin(IdeaPlugin.class).getModel();
        IdeaProject projectModel = ideaModel.getProject();
        JavaVersion projectSourceLanguageLevel = convertIdeaLanguageLevelToJavaVersion(projectModel.getLanguageLevel());

        final Jvm currentJvm = Jvm.current();
        final DefaultJavaRuntime javaRuntime = new DefaultJavaRuntime(currentJvm.getJavaHome(), currentJvm.getJavaVersion());

        DefaultIdeaProject out = new DefaultIdeaProject()
            .setName(projectModel.getName())
            .setJdkName(projectModel.getJdkName())
            .setLanguageLevel(new DefaultIdeaLanguageLevel(projectModel.getLanguageLevel().getLevel()))
            .setJavaSourceSettings(new DefaultJavaSourceSettings()
                .setSourceLanguageLevel(projectSourceLanguageLevel)
                .setJavaRuntime(javaRuntime));

        Map<String, DefaultIdeaModule> modules = new HashMap<String, DefaultIdeaModule>();
        for (IdeaModule module : projectModel.getModules()) {
            appendModule(modules, module, out, rootGradleProject, javaRuntime);
        }
        for (IdeaModule module : projectModel.getModules()) {
            buildDependencies(modules, module);
        }
        final Collection<DefaultIdeaModule> ideaModules = modules.values();

        final Set<JavaVersion> moduleBytecodeVersions = toSet(compact(collect(ideaModules, new Transformer<JavaVersion, DefaultIdeaModule>() {
            @Override
            public JavaVersion transform(DefaultIdeaModule defaultIdeaModule) {
                return defaultIdeaModule.getJavaSourceSettings().getTargetBytecodeLevel();
            }
        })));

        if (moduleBytecodeVersions.size() == 1) {
            // same bytecode versions declared for all modules
            for (DefaultIdeaModule ideaModule : ideaModules) {
                ideaModule.getJavaSourceSettings().setTargetBytecodeLevelInherited(true);
            }
            out.getJavaSourceSettings().setTargetBytecodeLevel(single(moduleBytecodeVersions));
        } else if (moduleBytecodeVersions.size() > 1) {
            final JavaVersion maxBytecodeLevel = Collections.max(moduleBytecodeVersions);
            out.getJavaSourceSettings().setTargetBytecodeLevel(maxBytecodeLevel);
        }

        out.setChildren(new LinkedList<DefaultIdeaModule>(ideaModules));
        return out;
    }

    private void buildDependencies(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule) {
        ideaModule.setOffline(offlineDependencyResolution);
        Set<Dependency> resolved = ideaModule.resolveDependencies();
        List<DefaultIdeaDependency> dependencies = new LinkedList<DefaultIdeaDependency>();
        for (Dependency dependency : resolved) {
            if (dependency instanceof SingleEntryModuleLibrary) {
                SingleEntryModuleLibrary d = (SingleEntryModuleLibrary) dependency;
                DefaultIdeaSingleEntryLibraryDependency defaultDependency = new org.gradle.tooling.internal.idea.DefaultIdeaSingleEntryLibraryDependency()
                    .setFile(d.getLibraryFile())
                    .setSource(d.getSourceFile())
                    .setJavadoc(d.getJavadocFile())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setExported(d.getExported());

                if (d.getModuleVersion() != null) {
                    defaultDependency.setGradleModuleVersion(new DefaultGradleModuleVersion(d.getModuleVersion()));
                }
                dependencies.add(defaultDependency);
            } else if (dependency instanceof ModuleDependency) {
                ModuleDependency d = (ModuleDependency) dependency;
                DefaultIdeaModuleDependency defaultDependency = new org.gradle.tooling.internal.idea.DefaultIdeaModuleDependency()
                    .setExported(d.getExported())
                    .setScope(new DefaultIdeaDependencyScope(d.getScope()))
                    .setDependencyModule(modules.get(d.getName()));
                dependencies.add(defaultDependency);
            }
        }
        modules.get(ideaModule.getName()).setDependencies(dependencies);
    }

    private void appendModule(Map<String, DefaultIdeaModule> modules, IdeaModule ideaModule, DefaultIdeaProject ideaProject, DefaultGradleProject rootGradleProject, DefaultJavaRuntime javaRuntime) {
        DefaultIdeaContentRoot contentRoot = new DefaultIdeaContentRoot()
            .setRootDirectory(ideaModule.getContentRoot())
            .setSourceDirectories(srcDirs(ideaModule.getSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setTestDirectories(srcDirs(ideaModule.getTestSourceDirs(), ideaModule.getGeneratedSourceDirs()))
            .setExcludeDirectories(ideaModule.getExcludeDirs());

        Project project = ideaModule.getProject();
        JavaPluginConvention javaPluginConvention = project.getConvention().findPlugin(JavaPluginConvention.class);
        JavaVersion projectSourceLanguageLevel = ideaProject.getJavaSourceSettings().getSourceLanguageLevel();
        JavaVersion moduleSourceLanguageLevel = javaPluginConvention != null ? javaPluginConvention.getSourceCompatibility() : projectSourceLanguageLevel;
        JavaVersion moduleTargetLanguageLevel = javaPluginConvention != null ? javaPluginConvention.getTargetCompatibility() : null;

        boolean sourceLanguageLevelInherited = projectSourceLanguageLevel.equals(moduleSourceLanguageLevel);

        DefaultIdeaModule defaultIdeaModule = new DefaultIdeaModule()
            .setName(ideaModule.getName())
            .setParent(ideaProject)
            .setGradleProject(rootGradleProject.findByPath(ideaModule.getProject().getPath()))
            .setContentRoots(Collections.singletonList(contentRoot))
            .setCompilerOutput(new DefaultIdeaCompilerOutput()
                .setInheritOutputDirs(ideaModule.getInheritOutputDirs() != null ? ideaModule.getInheritOutputDirs() : false)
                .setOutputDir(ideaModule.getOutputDir())
                .setTestOutputDir(ideaModule.getTestOutputDir()))
            .setJavaSourceSettings(new DefaultIdeaModuleJavaSourceSettings()
                .setSourceLanguageLevelInherited(sourceLanguageLevelInherited)
                .setSourceLanguageLevel(moduleSourceLanguageLevel)
                .setTargetBytecodeLevel(moduleTargetLanguageLevel)
                .setJavaRuntime(javaRuntime));
        modules.put(ideaModule.getName(), defaultIdeaModule);
    }

    private Set<DefaultIdeaSourceDirectory> srcDirs(Set<File> sourceDirs, Set<File> generatedSourceDirs) {
        Set<DefaultIdeaSourceDirectory> out = new LinkedHashSet<DefaultIdeaSourceDirectory>();
        for (File s : sourceDirs) {
            DefaultIdeaSourceDirectory sourceDirectory = new DefaultIdeaSourceDirectory().setDirectory(s);
            if (generatedSourceDirs.contains(s)) {
                sourceDirectory.setGenerated(true);
            }
            out.add(sourceDirectory);
        }
        return out;
    }

    public IdeaModelBuilder setOfflineDependencyResolution(boolean offlineDependencyResolution) {
        this.offlineDependencyResolution = offlineDependencyResolution;
        return this;
    }

    private JavaVersion convertIdeaLanguageLevelToJavaVersion(IdeaLanguageLevel ideaLanguageLevel) {
        String languageLevel = ideaLanguageLevel.getLevel();
        return JavaVersion.valueOf(languageLevel.replaceFirst("JDK", "VERSION"));
    }
}
