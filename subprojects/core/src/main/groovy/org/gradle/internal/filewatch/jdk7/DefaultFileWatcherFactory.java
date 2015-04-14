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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherFactory;

import java.util.concurrent.ExecutorService;

/**
 * Implementation of {@link FileWatcherFactory}
 */
public class DefaultFileWatcherFactory implements FileWatcherFactory, Stoppable {
    private final ExecutorService executor;

    public DefaultFileWatcherFactory(ExecutorFactory executorFactory) {
        this.executor = executorFactory.create("filewatcher");
    }

    @Override
    public FileWatcher createFileWatcher() {
        return new DefaultFileWatcher(executor);
    }

    @Override
    public void stop() {
        executor.shutdown();
    }
}
