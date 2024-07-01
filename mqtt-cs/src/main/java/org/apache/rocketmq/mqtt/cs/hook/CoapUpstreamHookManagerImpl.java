/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.mqtt.cs.hook;

import org.apache.rocketmq.mqtt.common.coap.CoapMessage;
import org.apache.rocketmq.mqtt.common.hook.CoapUpstreamHook;
import org.apache.rocketmq.mqtt.common.hook.CoapUpstreamHookManager;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CoapUpstreamHookManagerImpl implements CoapUpstreamHookManager {

    // todo: add CoapUpstreamHookEnum
    private CoapUpstreamHook[] upstreamHookList = new CoapUpstreamHook[2];
    private AtomicBoolean isAssembled = new AtomicBoolean(false);

    @Override
    public void addHook(int index, CoapUpstreamHook upstreamHook) {
        if (isAssembled.get()) {
            throw new IllegalArgumentException("Hook Was Assembled");
        }
        synchronized (upstreamHookList) {
            upstreamHookList[index] = upstreamHook;
        }
    }

    @Override
    public CompletableFuture<HookResult> doUpstreamHook(CoapMessage msg) {
        assembleNextHook();
        CompletableFuture<HookResult> hookResult = new CompletableFuture<>();
        if (upstreamHookList.length <= 0) {
            hookResult.complete(new HookResult(HookResult.SUCCESS, -1, null, null));
            return hookResult;
        }
        return upstreamHookList[0].doHook(msg);
    }

    private void assembleNextHook() {
        if (isAssembled.compareAndSet(false, true)) {
            synchronized (upstreamHookList) {
                for (int i = 0; i < upstreamHookList.length - 1; i++) {
                    CoapUpstreamHook upstreamHook = upstreamHookList[i];
                    if (upstreamHook.getNextHook() != null) {
                        continue;
                    }
                    upstreamHook.setNextHook(upstreamHookList[i + 1]);
                }
            }
        }
    }
}
