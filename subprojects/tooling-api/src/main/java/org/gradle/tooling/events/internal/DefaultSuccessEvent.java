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

package org.gradle.tooling.events.internal;

import org.gradle.tooling.Descriptor;
import org.gradle.tooling.events.SuccessEvent;
import org.gradle.tooling.events.SuccessOutcome;

/**
 * Default implementation of the {@code SuccessEvent} interface.
 */
public final class DefaultSuccessEvent extends BaseProgressEvent implements SuccessEvent {

    private final SuccessOutcome outcome;

    public DefaultSuccessEvent(long eventTime, String eventDescription, Descriptor descriptor, SuccessOutcome outcome) {
        super(eventTime, eventDescription, descriptor);
        this.outcome = outcome;
    }

    @Override
    public SuccessOutcome getOutcome() {
        return outcome;
    }

}