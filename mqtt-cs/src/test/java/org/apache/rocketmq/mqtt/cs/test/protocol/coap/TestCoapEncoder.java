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
package org.apache.rocketmq.mqtt.cs.test.protocol.coap;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import org.apache.rocketmq.mqtt.cs.protocol.coap.CoapEncoder;
import org.apache.rocketmq.mqtt.cs.protocol.coap.CoapMessage;
import org.apache.rocketmq.mqtt.cs.protocol.coap.CoapOption;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

@RunWith(MockitoJUnitRunner.class)
public class TestCoapEncoder {

    @Mock
    private ChannelHandlerContext channelHandlerContext;

    @InjectMocks
    private CoapEncoder coapEncoder;

    private CoapMessage msg;

    @Before
    public void setUp() {
        CoapOption option = new CoapOption(5, new byte[]{5, 6, 7, 8});
        List<CoapOption> options = new ArrayList<>();
        InetSocketAddress remoteAddress = new InetSocketAddress("195.0.30.1", 1234);
        options.add(option);
        msg = new CoapMessage(1, 0, 4, 64, 1234, new byte[]{1, 2, 3, 4}, options, new byte[]{0, 1}, remoteAddress);
    }

    @Test
    public void testEncode() throws Exception {
        ByteBuf out = Unpooled.buffer();
        coapEncoder.encode(channelHandlerContext, msg, out);
        byte[] result = new byte[out.readableBytes()];
        out.readBytes(result);

        byte[] expected = {(byte)0x44, (byte)0x40, (byte)0x04, (byte)0xD2, 1, 2, 3, 4, (byte)0x54, 5, 6, 7, 8, (byte)0xFF, 0, 1};
        assertArrayEquals("Encoding mismatch", expected, result);
    }

    @Test
    public void testEncodeWithNoPayload() throws Exception {
        msg.setPayload(new byte[0]); // Setting payload to empty

        ByteBuf out = Unpooled.buffer();
        coapEncoder.encode(channelHandlerContext, msg, out);
        byte[] result = new byte[out.readableBytes()];
        out.readBytes(result);

        byte[] expected = {(byte)0x44, (byte)0x40, (byte)0x04, (byte)0xD2, 1, 2, 3, 4, (byte)0x54, 5, 6, 7, 8};
        assertArrayEquals("Encoding with no payload mismatch", expected, result);
    }

    @Test
    public void testEncodeWithNoOptionsAndNoPayload() throws Exception {
        msg.setOptions(new ArrayList<>()); // Setting options to empty
        msg.setPayload(new byte[0]); // Setting payload to empty

        ByteBuf out = Unpooled.buffer();
        coapEncoder.encode(channelHandlerContext, msg, out);
        byte[] result = new byte[out.readableBytes()];
        out.readBytes(result);

        byte[] expected = {(byte)0x44, (byte)0x40, (byte)0x04, (byte)0xD2, 1, 2, 3, 4};
        assertArrayEquals("Encoding with no options and no payload mismatch", expected, result);
    }

}
