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
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.mqtt.common.coap.CoapRequestMessage;
import org.apache.rocketmq.mqtt.common.facade.LmqQueueStore;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.Constants;
import org.apache.rocketmq.mqtt.common.model.Message;
import org.apache.rocketmq.mqtt.common.model.StoreResult;
import org.apache.rocketmq.mqtt.common.util.MessageUtil;
import org.apache.rocketmq.mqtt.common.util.TopicUtils;
import org.apache.rocketmq.mqtt.ds.meta.FirstTopicManager;
import org.apache.rocketmq.mqtt.ds.upstream.coap.CoapUpstreamProcessor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Component
public class CoapPostProcessor implements CoapUpstreamProcessor {

    @Resource
    private LmqQueueStore lmqQueueStore;

    @Resource
    private FirstTopicManager firstTopicManager;

    @Override
    public CompletableFuture<HookResult> process(CoapRequestMessage msg) {
        CompletableFuture<StoreResult> r = put(msg);
        return r.thenCompose(storeResult -> HookResult.newHookResult(HookResult.SUCCESS, null, JSON.toJSONBytes(storeResult)));
    }

    public CompletableFuture<StoreResult> put(CoapRequestMessage coapMessage) {
        firstTopicManager.checkFirstTopicIfCreated(TopicUtils.decode(coapMessage.getUriPath()).getFirstTopic());
        String msgId = MessageClientIDSetter.createUniqID();
        long bornTime = System.currentTimeMillis();
        Set<String> queueNames = new HashSet<>();
        queueNames.add(coapMessage.getUriPath());

        Message message = MessageUtil.toMessage(coapMessage);
        message.setMsgId(msgId);
        message.setBornTimestamp(bornTime);
        message.putUserProperty(Constants.PROPERTY_COAP_CONTENT_FORMAT, String.valueOf(coapMessage.getContentFormat().value()));

        return lmqQueueStore.putCoapMessage(queueNames, message);

    }
}
