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

package org.gradle.launcher.continuous;

import org.gradle.internal.UncheckedException;
import org.gradle.util.Clock;

public class BlockingTriggerListener implements TriggerListener {
    private final Object lock;
    private final long timeoutMs;
    private TriggerDetails currentTrigger;

    public BlockingTriggerListener(long timeoutMs) {
        this.lock = new Object();
        this.currentTrigger = null;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Blocks until triggered() is called with a reason.
     */
    public TriggerDetails waitForTrigger() {
        try {
            // TODO: Add some logging
            synchronized (lock) {
                Clock started = new Clock();
                while (currentTrigger == null) {
                    if (timeoutMs == 0) {
                        // wait unconditionally
                        lock.wait();
                    } else {
                        // this should be integ test builds only
                        lock.wait(50);
                        long waited = started.getTimeInMs();
                        if (waited > timeoutMs) {
                            currentTrigger = new DefaultTriggerDetails("giving up after " + waited + " ms");
                        }
                    }
                }
                TriggerDetails lastReason = currentTrigger;
                currentTrigger = null;
                return lastReason;
            }
        } catch (InterruptedException e) {
            UncheckedException.throwAsUncheckedException(e);
            return null; // unreachable
        }
    }

    @Override
    public void triggered(TriggerDetails triggerInfo) {
        synchronized (lock) {
            currentTrigger = triggerInfo;
            lock.notify();
        }
    }
}