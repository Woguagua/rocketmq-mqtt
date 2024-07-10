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
package org.apache.rocketmq.mqtt.cs.protocol.coap.handler;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.mqtt.common.coap.*;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.Constants;
import org.apache.rocketmq.mqtt.common.model.Message;
import org.apache.rocketmq.mqtt.common.model.PullResult;
import org.apache.rocketmq.mqtt.cs.protocol.CoapPacketHandler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static org.apache.rocketmq.mqtt.common.model.Constants.PROPERTY_COAP_CONTENT_FORMAT;

@Component
public class CoapGetHandler implements CoapPacketHandler<CoapRequestMessage> {
    @Override
    public boolean preHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage) {
        // todo: add a InFlyCache, check redundant message using messageID or token
        return true;
    }

    @Override
    public void doHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, HookResult upstreamHookResult) {
        byte[] data = upstreamHookResult.getData();
        if (data != null) {
            PullResult pullResult = JSON.parseObject(data, PullResult.class);
            if (pullResult.getMessageList() != null && !pullResult.getMessageList().isEmpty()) {
                // todo: check when every message is not a string but a json list
                JSONArray jsonArray = new JSONArray();
                for (Message msg : pullResult.getMessageList()) {
                    if (msg.getUserProperty(PROPERTY_COAP_CONTENT_FORMAT) != null) {
                        switch (CoapMessageContentFormat.valueOf(Integer.parseInt(msg.getUserProperty(PROPERTY_COAP_CONTENT_FORMAT)))) {
                            case APPLICATION_JSON:
                                JSONObject jsonObject = JSON.parseObject(new String(msg.getPayload(), StandardCharsets.UTF_8));
                                jsonArray.add(jsonObject);
                                break;
                            case TEXT_PLAIN:
//                                jsonArray.add(new String(msg.getPayload(), StandardCharsets.UTF_8));
                                JSONObject jsonObject1 = new JSONObject();
                                jsonObject1.put("text", new String(msg.getPayload(), StandardCharsets.UTF_8));
                                jsonArray.add(jsonObject1);
                                break;
                            default:
                        }
                    }
                }
                CoapMessage response = new CoapMessage(
                        Constants.COAP_VERSION,
                        coapMessage.getType() == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                        coapMessage.getTokenLength(),
                        CoapMessageCode.CONTENT,
                        coapMessage.getMessageId(),
                        coapMessage.getToken(),
                        jsonArray.toString().getBytes(StandardCharsets.UTF_8),
                        coapMessage.getRemoteAddress()
                );
                response.addOption(new CoapMessageOption(CoapMessageOptionNumber.CONTENT_FORMAT, CoapMessageContentFormat.APPLICATION_JSON.toByteArray()));
                ctx.writeAndFlush(response);
            }
        }
    }
}
