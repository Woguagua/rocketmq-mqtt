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

package org.apache.rocketmq.mqtt.cs.session;

import org.apache.rocketmq.mqtt.common.model.Message;
import org.apache.rocketmq.mqtt.common.model.Queue;
import org.apache.rocketmq.mqtt.common.model.QueueOffset;
import org.apache.rocketmq.mqtt.common.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CoapSession {
    private static Logger logger = LoggerFactory.getLogger(CoapSession.class);
    private InetSocketAddress address;
    private int messageId;
    private byte[] token;
    private int messageNum = 0;
    private long subscribeTime;
    private volatile int pullSize;

    private Subscription subscription;
    private Map<Queue, QueueOffset> offsetMap = new ConcurrentHashMap<>(16);
    Map<Queue, LinkedHashSet<Message>> sendingMessages = new ConcurrentHashMap<>(16);

    public CoapSession() {}

    public void refreshSubscribeTime() {
        this.subscribeTime = System.currentTimeMillis();
    }

    public QueueOffset getQueueOffset(Queue queue) {
        if (queue == null) {
            throw new RuntimeException("queue is null");
        }
        return offsetMap.get(queue);
    }

    public void freshQueue(Set<Queue> queues) {
        if (this.subscription == null) {
            throw new RuntimeException("subscription is null");
        }
        if (queues == null) {
            logger.warn("queues is null when freshQueue,{},{}", this.address, this.subscription);
            return;
        }

        for (Queue memQueue: offsetMap.keySet()) {
            if (!queues.contains(memQueue)) {
                offsetMap.remove(memQueue);
            }
        }

        // init queueOffset
        for (Queue nowQueue : queues) {
            if (!offsetMap.containsKey(nowQueue)) {
                QueueOffset queueOffset = new QueueOffset();
                offsetMap.put(nowQueue, queueOffset);
                // todo: this.markPersistOffsetFlag(true);
            }
        }

        for (Queue memQueue : sendingMessages.keySet()) {
            if (!queues.contains(memQueue)) {
                sendingMessages.remove(memQueue);
            }
        }

        if (queues.isEmpty()) {
            logger.warn("queues is empty when freshQueue,{},{}", this.address, this.subscription);
        }
    }

    public void addOffset(Queue queue, QueueOffset offset) {
        offsetMap.put(queue, offset);
    }

    public void updateQueueOffset(Queue queue, Message message) {
        if (!offsetMap.containsKey(queue)) {
            logger.warn("failed update queue offset,not found queueOffset,{},{},{}", this.address, this.subscription,
                    queue);
            return;
        }
        QueueOffset queueOffset = offsetMap.get(queue);
        queueOffset.setOffset(message.getOffset() + 1);
    }

    public boolean addSendingMessages(Queue queue, List<Message> messages) {
        if (queue == null) {
            throw new RuntimeException("queue is null");
        }
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        if (subscription.isShare()) {
            return true;
        }
        if (!sendingMessages.containsKey(queue)) {
            sendingMessages.putIfAbsent(queue, new LinkedHashSet<>(8));
        }
        if (!offsetMap.containsKey(queue)) {
            logger.warn("not found queueOffset,{},{},{}", this.address, this.subscription, queue);
            return false;
        }
        boolean add =false;
        QueueOffset queueOffset = offsetMap.get(queue);
        for (Message message : messages) {
            if (message.getOffset() < queueOffset.getOffset() && queueOffset.getOffset() != Long.MAX_VALUE) {
                continue;
            }
            synchronized (this) {
                if (sendingMessages.get(queue).add(message.copy())) {
                    add = true;
                }
            }
        }
        return add;
    }

    public boolean sendingMessageIsEmpty(Queue queue) {
        if (queue == null) {
            throw new RuntimeException("queue is null");
        }
        LinkedHashSet<Message> messages = sendingMessages.get(queue);
        if (messages == null) {
            return true;
        }
        synchronized (this) {
            return messages.isEmpty();
        }
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setAddress(InetSocketAddress address) {
        this.address = address;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public byte[] getToken() {
        return token;
    }

    public void setToken(byte[] token) {
        this.token = token;
    }

    public int getMessageNum() {
        return messageNum;
    }

    public void setMessageNum(int messageNum) {
        this.messageNum = messageNum;
    }

    public long getSubscribeTime() {
        return subscribeTime;
    }

    public void setSubscribeTime(long subscribeTime) {
        this.subscribeTime = subscribeTime;
    }

    public Subscription getSubscription() {
        return subscription;
    }

    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
    }

    public int getPullSize() {
        return pullSize;
    }

    public void setPullSize(int pullSize) {
        this.pullSize = pullSize;
    }
}
