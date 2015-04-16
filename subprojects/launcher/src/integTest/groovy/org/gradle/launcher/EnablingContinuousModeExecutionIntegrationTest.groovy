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

package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.launcher.exec.ContinuousModeBuildActionExecuter
import spock.lang.Ignore

@Ignore
// @IgnoreIf({ !JavaVersion.current().java7Compatible })
public class EnablingContinuousModeExecutionIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.withArgument("--watch")
        setBuildTimeouts()
    }

    def "can enable continuous mode"() {
        expect:
        succeeds("tasks")
    }

    @Ignore("Output isn't captured when this comes out")
    def "warns about incubating feature"() {
        expect:
        succeeds("tasks")
        output.contains("Continuous mode is an incubating feature.")
    }

    @Ignore("Output isn't captured when this comes out")
    def "prints useful messages when in continuous mode"() {
        given:
        setBuildTimeouts(2)
        expect:
        succeeds("tasks")
        output.contains("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.")
        output.contains("Rebuild triggered due to ")
    }

    def setBuildTimeouts(int buildCount=1, int timeToWaitForTrigger=1000) {
        executer.withArgument("-D${ContinuousModeBuildActionExecuter.RUNAWAY_COUNT_PROPERTY}=${buildCount}")
        executer.withArgument("-D${ContinuousModeBuildActionExecuter.RUNAWAY_TIMEOUT_PROPERTY}=${timeToWaitForTrigger}")
    }
}
