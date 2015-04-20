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

package org.gradle.launcher.continuous

import org.gradle.internal.filewatch.FileWatcherService
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class FileWatchStrategyTest extends Specification {
    def listener = Mock(TriggerListener)

    def "registers with file watcher"() {
        given:
        def fileWatcherFactory = Mock(FileWatcherService)
        when:
        def fileWatchStrategy = new FileWatchStrategy(listener, fileWatcherFactory)
        then:
        1 * fileWatcherFactory.watch(
                { !it.directoryTrees.empty },
                { it instanceof FileWatchStrategy.FileChangeCallback })
    }

    def "file watch change triggers listener"() {
        given:
        def callback = new FileWatchStrategy.FileChangeCallback(listener)
        when:
        callback.run()
        then:
        1 * listener.triggered(_)
    }
}
