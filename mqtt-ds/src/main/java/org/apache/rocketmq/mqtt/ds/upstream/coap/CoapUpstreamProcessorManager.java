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

package org.apache.rocketmq.mqtt.ds.upstream.coap;

import org.apache.rocketmq.mqtt.common.hook.CoapUpstreamHook;
import org.apache.rocketmq.mqtt.common.hook.Hook;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.coap.CoapMessage;
import org.apache.rocketmq.mqtt.ds.upstream.coap.processor.CoapDeleteProcessor;
import org.apache.rocketmq.mqtt.ds.upstream.coap.processor.CoapGetProcessor;
import org.apache.rocketmq.mqtt.ds.upstream.coap.processor.CoapPostProcessor;
import org.apache.rocketmq.mqtt.ds.upstream.coap.processor.CoapPutProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Component
public class CoapUpstreamProcessorManager implements CoapUpstreamHook {
    public static Logger logger = LoggerFactory.getLogger(CoapUpstreamProcessorManager.class);
    public CoapUpstreamHook nextUpstreamHook;

    @Resource
    CoapGetProcessor coapGetProcessor;

    @Resource
    CoapPostProcessor coapPostProcessor;

    @Resource
    CoapPutProcessor coapPutProcessor;

    @Resource
    CoapDeleteProcessor coapDeleteProcessor;

    @Override
    public void setNextHook(Hook hook) {
        this.nextUpstreamHook = (CoapUpstreamHook) hook;
    }

    @Override
    public Hook getNextHook() {
        return this.nextUpstreamHook;
    }

    @Override
    public CompletableFuture<HookResult> doHook(CoapMessage msg) {
        try {
            CompletableFuture<HookResult> result = processCoapMessage(msg);
            if (nextUpstreamHook == null) {
                return result;
            }
            return result.thenCompose(hookResult -> {
                if (!hookResult.isSuccess()) {
                    CompletableFuture<HookResult> nextHookResult = new CompletableFuture<>();
                    nextHookResult.complete(hookResult);
                    return nextHookResult;
                }
                return nextUpstreamHook.doHook(msg);
            });

        } catch (Throwable t) {
            logger.error("", t);
            CompletableFuture<HookResult> result = new CompletableFuture<>();
            result.completeExceptionally(t);
            return result;
        }
    }

    public void register() {
        // todo: Add Coap Hook
    }

    public CompletableFuture<HookResult> processCoapMessage(CoapMessage msg) {
        switch (msg.getCode()) {
            case GET:
                return coapGetProcessor.process(msg);
            case POST:
                return coapPostProcessor.process(msg);
            case PUT:
                return coapPutProcessor.process(msg);
            case DELETE:
                return coapDeleteProcessor.process(msg);
            default:
        }
        CompletableFuture<HookResult> hookResult = new CompletableFuture<>();
        hookResult.complete(new HookResult(HookResult.FAIL, "InvalidCoapMsgCode", null));
        return hookResult;
    }
}
