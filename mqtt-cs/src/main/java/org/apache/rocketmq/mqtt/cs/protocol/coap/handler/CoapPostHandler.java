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

import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.mqtt.common.coap.CoapMessageCode;
import org.apache.rocketmq.mqtt.common.coap.CoapMessageType;
import org.apache.rocketmq.mqtt.common.coap.CoapRequestMessage;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.Constants;
import org.apache.rocketmq.mqtt.cs.protocol.CoapPacketHandler;
import org.apache.rocketmq.mqtt.common.coap.CoapMessage;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class CoapPostHandler implements CoapPacketHandler<CoapRequestMessage> {
    @Override
    public boolean preHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage) {
        // todo: add a cache, check redundant message using messageID or token, respond according to the header, check payload format
        return true;
    }

    @Override
    public void doHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, HookResult upstreamHookResult) {
        if (upstreamHookResult.isSuccess()) {
            doResponseSuccess(ctx, coapMessage);
        } else {
            doResponseFail(ctx, coapMessage, upstreamHookResult.getRemark());
        }
    }

    public void doResponseFail(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, String errContent) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                coapMessage.getType() == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                coapMessage.getTokenLength(),
                CoapMessageCode.INTERNAL_SERVER_ERROR,
                coapMessage.getMessageId(),
                coapMessage.getToken(),
                null,
                errContent.getBytes(),
                coapMessage.getRemoteAddress()
        );
        ctx.writeAndFlush(response);
    }

    public void doResponseSuccess(ChannelHandlerContext ctx, CoapRequestMessage coapMessage) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                coapMessage.getType() == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                coapMessage.getTokenLength(),
                CoapMessageCode.CREATED,
                coapMessage.getMessageId(),
                coapMessage.getToken(),
                null,
                null,
                coapMessage.getRemoteAddress()
        );
        ctx.writeAndFlush(response);
    }
}
