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
package org.apache.rocketmq.mqtt.cs.protocol.coap;

import io.netty.channel.ChannelException;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.rocketmq.mqtt.common.coap.CoapMessage;
import org.apache.rocketmq.mqtt.common.hook.CoapUpstreamHookManager;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapDeleteHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapGetHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapPostHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapPutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Component
public class CoapPacketDispatcher extends SimpleChannelInboundHandler<CoapMessage> {

    private static Logger logger = LoggerFactory.getLogger(CoapPacketDispatcher.class);
    @Resource
    private CoapGetHandler coapGetHandler;

    @Resource
    private CoapPostHandler coapPostHandler;

    @Resource
    private CoapPutHandler coapPutHandler;

    @Resource
    private CoapDeleteHandler coapDeleteHandler;

    @Resource
    private CoapUpstreamHookManager upstreamHookManager;



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CoapMessage msg) throws Exception {

        boolean preResult = preHandler(ctx, msg);
        if (!preResult) {
            return;
        }
        CompletableFuture<HookResult> upstreamHookResult;
        try {
            upstreamHookResult = upstreamHookManager.doUpstreamHook(msg);
            if (upstreamHookResult == null) {
                _channelRead0(ctx, msg, null);
                return;
            }
        } catch (Throwable t) {
            logger.error("", t);
            throw new ChannelException(t.getMessage());
        }
        upstreamHookResult.whenComplete((hookResult, throwable) -> {
            if (throwable != null) {
                logger.error("", throwable);
                ctx.fireExceptionCaught(new ChannelException(throwable.getMessage()));
                return;
            }
            if (hookResult == null) {
                ctx.fireExceptionCaught(new ChannelException("Coap UpstreamHook Result Unknown"));
                return;
            }
            try {
                _channelRead0(ctx, msg, hookResult);
            } catch (Throwable t) {
                logger.error("", t);
                ctx.fireExceptionCaught(new ChannelException(t.getMessage()));
            }
        });
    }

    private  void _channelRead0(ChannelHandlerContext ctx, CoapMessage msg, HookResult upstreamHookResult) {
        switch (msg.getCode()) {
            case GET:
                coapGetHandler.doHandler(ctx, msg, upstreamHookResult);
                break;
            case POST:
                coapPostHandler.doHandler(ctx, msg, upstreamHookResult);
                break;
            case PUT:
                coapPutHandler.doHandler(ctx, msg, upstreamHookResult);
                break;
            case DELETE:
                coapDeleteHandler.doHandler(ctx, msg, upstreamHookResult);
                break;
            default:
        }
    }

    private boolean preHandler(ChannelHandlerContext ctx, CoapMessage msg) {
        switch (msg.getCode()) {
            case GET:
                return coapGetHandler.preHandler(ctx, msg);
            case POST:
                return coapPostHandler.preHandler(ctx, msg);
            case PUT:
                return coapPutHandler.preHandler(ctx, msg);
            case DELETE:
                return coapDeleteHandler.preHandler(ctx, msg);
            default:
                return false;
        }
    }

}
