/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.CompositeFileTree;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.tasks.TaskState;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.filewatch.*;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by lari on 09/04/15.
 */
public class WatchModeBuildController extends AbstractBuildController {
    private final GradleLauncherFactory gradleLauncherFactory;
    private final StartParameter startParameter;
    private final BuildRequestContext buildRequestContext;
    private final FileWatcherFactory fileWatcherFactory;
    private DefaultGradleLauncher currentLauncher;

    public WatchModeBuildController(GradleLauncherFactory gradleLauncherFactory, StartParameter startParameter, BuildRequestContext buildRequestContext, FileWatcherFactory fileWatcherFactory) {
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.startParameter = startParameter;
        this.buildRequestContext = buildRequestContext;
        this.fileWatcherFactory = fileWatcherFactory;
    }

    @Override
    protected DefaultGradleLauncher getLauncher() {
        if(currentLauncher == null) {
            currentLauncher = (DefaultGradleLauncher)gradleLauncherFactory.newInstance(startParameter, buildRequestContext);
        }
        return currentLauncher;
    }

    @Override
    void stopLauncher() {
        currentLauncher.stop();
        currentLauncher = null;
    }

    @Override
    public GradleInternal run() {
        GradleInternal gradle = null;
        while(!buildRequestContext.getCancellationToken().isCancellationRequested()) {
            System.out.println("----- WATCH MODE -----");
            gradle = getGradle();
            TaskInputsTaskListener taskInputsListener = new TaskInputsTaskListener();
            gradle.addListener(taskInputsListener);
            super.run();
            FileWatcher fileWatcher = fileWatcherFactory.createFileWatcher();
            WaitingFileWatchListener listener = new WaitingFileWatchListener();
            fileWatcher.watch(taskInputsListener.inputs, listener);
            System.out.println("-------- WAITING FOR CHANGES -------");
            listener.waitForChanges(buildRequestContext.getCancellationToken());
            fileWatcher.stop();
        }
        return gradle;
    }

    private static class WaitingFileWatchListener implements FileWatchListener {
        CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void changesDetected(FileWatchEvent event) {
            latch.countDown();
        }

        public void waitForChanges(BuildCancellationToken token) {
            while(!token.isCancellationRequested()) {
                try {
                    if (latch.await(1, TimeUnit.SECONDS)) {
                        return;
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private static class TaskInputsTaskListener implements TaskExecutionListener {
        FileWatchInputs inputs = new DefaultFileWatchInputs();

        @Override
        public void beforeExecute(Task task) {

        }

        @Override
        public void afterExecute(Task task, TaskState state) {
            addInputFiles(task.getInputs().getFiles());
        }

        private void addInputFiles(FileCollection files) {
            handleFileCollection(files);
        }

        private void handleFileCollection(FileCollection fileCollection) {
            if(fileCollection instanceof UnionFileCollection) {
                for (FileCollection source : ((UnionFileCollection) fileCollection).getSources()) {
                    handleFileCollection(source);
                }
                return;
            }
            if(fileCollection instanceof FileTreeAdapter) {
                MinimalFileTree minimalFileTree = ((FileTreeAdapter)fileCollection).getTree();
                if(minimalFileTree instanceof DirectoryTree) {
                    addDirectoryTree((DirectoryTree) minimalFileTree);
                    return;
                }
            }
            FileTree fileTree = fileCollection.getAsFileTree();
            if (fileTree instanceof CompositeFileTree) {
                for (FileCollection sourceCollection : ((CompositeFileTree) fileTree).getSourceCollections()) {
                    handleFileCollection(sourceCollection);
                }
                return;
            }

            for(File file : fileCollection.getFiles()) {
                addFile(file);
            }
        }

        private void addFile(File file) {
            inputs.watch(file);
        }

        private void addDirectoryTree(DirectoryTree minimalFileTree) {
            inputs.watch(minimalFileTree);
        }
    }
}
