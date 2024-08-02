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
import org.apache.rocketmq.common.ThreadFactoryImpl;
import org.apache.rocketmq.mqtt.common.facade.RetainedPersistManager;
import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.Constants;
import org.apache.rocketmq.mqtt.common.model.Message;
import org.apache.rocketmq.mqtt.common.model.Subscription;
import org.apache.rocketmq.mqtt.common.model.CoapMessage;
import org.apache.rocketmq.mqtt.common.model.CoapRequestMessage;
import org.apache.rocketmq.mqtt.common.model.CoapMessageType;
import org.apache.rocketmq.mqtt.common.model.CoapMessageOption;
import org.apache.rocketmq.mqtt.common.model.CoapMessageOptionNumber;
import org.apache.rocketmq.mqtt.common.model.CoapMessageCode;
import org.apache.rocketmq.mqtt.common.util.TopicUtils;
import org.apache.rocketmq.mqtt.cs.protocol.CoapPacketHandler;
import org.apache.rocketmq.mqtt.cs.session.CoapSession;
import org.apache.rocketmq.mqtt.cs.session.infly.CoapResponseCache;
import org.apache.rocketmq.mqtt.cs.session.infly.CoapRetryManager;
import org.apache.rocketmq.mqtt.cs.session.loop.CoapSessionLoop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class CoapSubscribeHandler implements CoapPacketHandler<CoapRequestMessage> {

    private static Logger logger = LoggerFactory.getLogger(CoapSubscribeHandler.class);

    @Resource
    private CoapSessionLoop sessionLoop;

    @Resource
    private RetainedPersistManager retainedPersistManager;

    @Resource
    private CoapResponseCache coapResponseCache;

    @Resource
    private CoapRetryManager coapRetryManager;

    private ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, new ThreadFactoryImpl("check_coap_subscribe_future"));

    @Override
    public boolean preHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage) {
        // todo: check token if connection mode
        return true;
    }

    @Override
    public void doHandler(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, HookResult upstreamHookResult) {
        // todo: response ack
        if (!upstreamHookResult.isSuccess()) {
            doResponseFail(ctx, coapMessage, upstreamHookResult.getRemark());
            return;
        }

        Subscription subscription = new Subscription();
        subscription.setQos(coapMessage.getQosLevel().value());
        subscription.setTopicFilter(TopicUtils.normalizeTopic(coapMessage.getTopic()));

        InetSocketAddress address = coapMessage.getRemoteAddress();
        CoapSession session = sessionLoop.getSession(address);
        if (session != null) {
            session.refreshSubscribeTime();
            doResponseSuccess(ctx, coapMessage, session);
            return;
        }

        CoapSession newSession = new CoapSession();
        newSession.setAddress(address);
        newSession.setToken(coapMessage.getToken());
        newSession.setSubscribeTime(System.currentTimeMillis());
        newSession.setSubscription(subscription);
        CompletableFuture<Void> future = new CompletableFuture<>();
        // todo: setFuture
        scheduler.schedule(() -> {
            if (!future.isDone()) {
                future.complete(null);
            }
        }, 1, TimeUnit.SECONDS);
        try {
            sessionLoop.addSession(newSession, future);
            doResponseSuccess(ctx, coapMessage, newSession);

            future.thenAccept(aVoid -> {
                if (!ctx.channel().isActive()) {
                    return;
                }
                // todo: removeFuture
                doResponseSuccess(ctx, coapMessage, newSession);
                sendRetainMessage(ctx, newSession);
            });
        } catch (Exception e) {
            logger.error("Coap Subscribe:{}", coapMessage.getRemoteAddress(), e);
        }

    }

    private void sendRetainMessage(ChannelHandlerContext ctx, CoapSession session) {
        CompletableFuture<Message> retainedMessage = retainedPersistManager.getRetainedMessage(session.getSubscription().getTopicFilter());
        retainedMessage.whenComplete(((message, throwable) -> {
            if (message == null) {
                return;
            }
            session.messageNumIncrement();
            CoapMessage sendMessage = new CoapMessage(
                    Constants.COAP_VERSION,
                    session.getSubscription().getQos() == 0 ? CoapMessageType.NON : CoapMessageType.CON,
                    session.getToken().length,
                    CoapMessageCode.CONTENT,
                    session.getMessageId() + session.getMessageNum(),
                    session.getToken(),
                    message.getPayload(),
                    session.getAddress()
            );
            ctx.writeAndFlush(sendMessage);
            if (session.getSubscription().getQos() > 0) {
                coapRetryManager.addRetryMessage(sendMessage);
            }
        }));
    }

    public void doResponseFail(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, String errContent) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                CoapMessageType.ACK,
                coapMessage.getTokenLength(),
                CoapMessageCode.INTERNAL_SERVER_ERROR,
                coapMessage.getMessageId() + 1,
                coapMessage.getToken(),
                errContent.getBytes(StandardCharsets.UTF_8),
                coapMessage.getRemoteAddress()
        );
        response.addOption(new CoapMessageOption(CoapMessageOptionNumber.OBSERVE, intToByteArray(1)));
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(response);
            coapResponseCache.put(response);
        } else {
            System.out.println("Channel is not active");
        }
    }


    // change an integer into a byte array with length 3
    private byte[] intToByteArray(int value) {
        byte[] byteArray = new byte[3];
        byteArray[0] = (byte) (value >> 16);
        byteArray[1] = (byte) (value >> 8);
        byteArray[2] = (byte) (value);
        return byteArray;
    }

    public void doResponseSuccess(ChannelHandlerContext ctx, CoapRequestMessage coapMessage, CoapSession session) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                coapMessage.getType() == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                coapMessage.getTokenLength(),
                CoapMessageCode.CONTENT,
                coapMessage.getMessageId(),
                coapMessage.getToken(),
                "Hello, I have accept your request successfully!".getBytes(StandardCharsets.UTF_8),
                coapMessage.getRemoteAddress()
        );
        response.addOption(new CoapMessageOption(CoapMessageOptionNumber.OBSERVE, intToByteArray(session.getMessageNum())));
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(response);
            coapResponseCache.put(response);
        } else {
            System.out.println("Channel is not active");
        }
    }

}