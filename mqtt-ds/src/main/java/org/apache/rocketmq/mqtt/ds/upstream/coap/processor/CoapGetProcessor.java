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

package org.apache.rocketmq.mqtt.ds.upstream.coap.processor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.rocketmq.mqtt.common.coap.CoapRequestMessage;
import org.apache.rocketmq.mqtt.common.facade.LmqQueueStore;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.PullResult;
import org.apache.rocketmq.mqtt.common.model.Queue;
import org.apache.rocketmq.mqtt.common.model.QueueOffset;
import org.apache.rocketmq.mqtt.common.util.TopicUtils;
import org.apache.rocketmq.mqtt.ds.upstream.coap.CoapUpstreamProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class CoapGetProcessor implements CoapUpstreamProcessor {
    private static Logger logger = LoggerFactory.getLogger(CoapGetProcessor.class);

    @Resource
    private LmqQueueStore lmqQueueStore;

    @Override
    public CompletableFuture<HookResult> process(CoapRequestMessage msg) {
        Set<Queue> queues = new HashSet<>();
        Set<String> brokers;
        String firstTopic = TopicUtils.decode(msg.getUriPath()).getFirstTopic();
        brokers = lmqQueueStore.getReadableBrokers(firstTopic);
        if (brokers == null || brokers.isEmpty()) {
            CompletableFuture<HookResult> failureResult = new CompletableFuture<>();
            failureResult.complete(new HookResult(HookResult.FAIL, "No valid broker", null));
            return failureResult;
        }
        for (String broker : brokers) {
            Queue moreQueue = new Queue();
            moreQueue.setQueueName(msg.getUriPath());
            moreQueue.setBrokerName(broker);
            queues.add(moreQueue);
        }
        if (queues.isEmpty()) {
            CompletableFuture<HookResult> failureResult = new CompletableFuture<>();
            failureResult.complete(new HookResult(HookResult.FAIL, "No valid queue", null));
            return failureResult;
        }
        // todo: now just pull the first queue, must be transfer to iterating all queue
        Queue queue = queues.iterator().next();
        CompletableFuture<PullResult> result = new CompletableFuture<>();
        result.whenComplete((pullResult, throwable) -> {
            if (throwable != null) {
                logger.error("", throwable);
            }
            if (PullResult.PULL_SUCCESS == pullResult.getCode()) {
                // response data
            } else if (PullResult.PULL_OFFSET_MOVED == pullResult.getCode()) {
                // reset offset and pull again
            } else {
                logger.error("response:{}", JSONObject.toJSONString(pullResult));
            }
        });
        CompletableFuture<PullResult> pullResult = lmqQueueStore.pullMessage(firstTopic, queue, new QueueOffset(0), 32);
        pullResult.whenComplete((pullResult1, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(throwable);
            } else {
                result.complete(pullResult1);
            }
        });

        return result.thenCompose(pullResult2 -> HookResult.newHookResult(HookResult.SUCCESS, null, JSON.toJSONBytes(pullResult2)));
    }
}
