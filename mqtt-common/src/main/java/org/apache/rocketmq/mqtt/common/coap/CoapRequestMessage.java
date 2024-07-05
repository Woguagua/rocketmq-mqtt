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

package org.apache.rocketmq.mqtt.common.coap;

import java.net.InetSocketAddress;
import java.util.List;

public class CoapRequestMessage extends CoapMessage{
    String uriPath;

    public CoapRequestMessage(int version, CoapMessageType type, int tokenLength, CoapMessageCode code, int messageId, byte[] token, List<CoapMessageOption> options, byte[] payload, InetSocketAddress remoteAddress) {
        super(version, type, tokenLength, code, messageId, token, options, payload, remoteAddress);
    }

    public CoapRequestMessage(int version, int type, int tokenLength, int code, int messageId, byte[] token, List<CoapMessageOption> options, byte[] payload, InetSocketAddress remoteAddress) {
        super(version, type, tokenLength, code, messageId, token, options, payload, remoteAddress);
    }

    public CoapRequestMessage(int version, CoapMessageType type, int tokenLength, CoapMessageCode code, int messageId, byte[] token, InetSocketAddress remoteAddress) {
        super(version, type, tokenLength, code, messageId, token, remoteAddress);
    }

    public String getUriPath() {
        return uriPath;
    }

    public void setUriPath(String uriPath) {
        this.uriPath = uriPath;
    }

}
