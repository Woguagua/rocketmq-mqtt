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
import org.apache.rocketmq.mqtt.common.coap.CoapRequestMessage;
import org.apache.rocketmq.mqtt.common.hook.CoapUpstreamHookManager;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapDeleteHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapGetHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapPostHandler;
import org.apache.rocketmq.mqtt.cs.protocol.coap.handler.CoapPutHandler;
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
public class CoapPacketDispatcher extends SimpleChannelInboundHandler<CoapRequestMessage> {

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

    @Resource
    CoapGetProcessor coapGetProcessor;

    @Resource
    CoapPostProcessor coapPostProcessor;

    @Resource
    CoapPutProcessor coapPutProcessor;

    @Resource
    CoapDeleteProcessor coapDeleteProcessor;



    @Override
    protected void channelRead0(ChannelHandlerContext ctx, CoapRequestMessage msg) throws Exception {

        boolean preResult = preHandler(ctx, msg);
        if (!preResult) {
            return;
        }
        CompletableFuture<HookResult> processResult;
        try {
//            processResult = upstreamHookManager.doUpstreamHook(msg);
            processResult = processCoapMessage(msg);
            if (processResult == null) {
                _channelRead0(ctx, msg, null);
                return;
            }
        } catch (Throwable t) {
            logger.error("", t);
            throw new ChannelException(t.getMessage());
        }
        processResult.whenComplete((hookResult, throwable) -> {
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

    private  void _channelRead0(ChannelHandlerContext ctx, CoapRequestMessage msg, HookResult processResult) {
        switch (msg.getCode()) {
            case GET:
                coapGetHandler.doHandler(ctx, msg, processResult);
                break;
            case POST:
                coapPostHandler.doHandler(ctx, msg, processResult);
                break;
            case PUT:
                coapPutHandler.doHandler(ctx, msg, processResult);
                break;
            case DELETE:
                coapDeleteHandler.doHandler(ctx, msg, processResult);
                break;
            default:
        }
    }

    private boolean preHandler(ChannelHandlerContext ctx, CoapRequestMessage msg) {
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

    public CompletableFuture<HookResult> processCoapMessage(CoapRequestMessage msg) {
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
