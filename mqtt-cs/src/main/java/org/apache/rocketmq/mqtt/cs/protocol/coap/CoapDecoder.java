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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CoapDecoder extends MessageToMessageDecoder<DatagramPacket> {

    /** Coap Version */
    public static final int VERSION = 1;

    /** Coap Division Marker for Payload */
    public static final int PAYLOAD_MARKER = 0xFF;

    /** Coap Token Length must be 0~8 */
    public static final int MAX_TOKEN_LENGTH = 8;

    @Override
    public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) throws Exception {

        ByteBuf in = packet.content();

        // The length of Coap message is at least 4 bytes.
        if (in.readableBytes() < 4) {
            // todo: Return 4.00 Error Response
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }

        // Handle first byte, including version, type, and token length.
        int firstByte = in.readUnsignedByte();
        int version = (firstByte >> 6) & 0x03;
        if (version != VERSION) {
            // todo: Return 4.00 Error Response
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        int type = (firstByte >> 4) & 0x03;
        int tokenLength = firstByte & 0x0F;
        if (tokenLength > MAX_TOKEN_LENGTH) {
            // todo: Return 4.00 Error Response
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }

        // Handle code
        int code = in.readUnsignedByte();
        if (code <= 0 || code >= 5) {
            // todo: Return 4.00 Error Response
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        // Handle messageID
        int messageId = in.readUnsignedShort();

        // Handle token
        if (in.readableBytes() < tokenLength) {
            // Return 4.00 Response
            CoapMessage response = new CoapMessage(
                    VERSION,
                    type == CoapMessageType.CON.value() ? CoapMessageType.ACK : CoapMessageType.NON,
                    tokenLength,
                    CoapMessageCode.BAD_REQUEST,
                    messageId,
                    null,
                    null,
                    "Format-Error: The length of remaining readable bytes is less than tokenLength!".getBytes(StandardCharsets.UTF_8),
                    packet.sender()
            );
            ctx.writeAndFlush(response);
            // todo: Sliping unread bytes
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        byte[] token = new byte[tokenLength];
        in.readBytes(token);

        // Handle options
        int nextByte = 0;
        int optionNumber = 0;
        List<CoapMessageOption> options = new ArrayList<CoapMessageOption>();
        while (in.readableBytes() > 0) {

            nextByte = in.readUnsignedByte();
            if (nextByte == PAYLOAD_MARKER) {
                break;
            }

            int optionDelta = nextByte >> 4;
            int optionLength = nextByte & 0x0F;

            if (optionDelta == 13) {
                optionDelta += in.readUnsignedByte();
            } else if (optionDelta == 14) {
                optionDelta += 255 + in.readUnsignedShort();
            } else if (optionDelta == 15) {
                // Return 4.00 Response
                CoapMessage response = new CoapMessage(
                        VERSION,
                        type == CoapMessageType.CON.value() ? CoapMessageType.ACK : CoapMessageType.NON,
                        tokenLength,
                        CoapMessageCode.BAD_REQUEST,
                        messageId,
                        token,
                        null,
                        "Format-Error: OptionDelta can not be 15!".getBytes(StandardCharsets.UTF_8),
                        packet.sender()
                );
                ctx.writeAndFlush(response);
                return;
            }

            optionNumber += optionDelta;    // current optionNumber = last optionNumber + optionDelta

            if (!CoapMessageOptionNumber.isValid(optionNumber)) {
                // Return 4.02 Response
                CoapMessage response = new CoapMessage(
                        VERSION,
                        type == CoapMessageType.CON.value() ? CoapMessageType.ACK : CoapMessageType.NON,
                        tokenLength,
                        CoapMessageCode.BAD_OPTION,
                        messageId,
                        token,
                        null,
                        "Format-Error: Option number is not defined!".getBytes(StandardCharsets.UTF_8),
                        packet.sender()
                );
                ctx.writeAndFlush(response);
                return;
            }


            if (optionLength == 13) {
                optionLength += in.readUnsignedByte();
            } else if (optionLength == 14) {
                optionLength += 255 + in.readUnsignedShort();
            } else if (optionLength == 15) {
                // Return 4.00 Response
                CoapMessage response = new CoapMessage(
                        VERSION,
                        type == CoapMessageType.CON.value() ? CoapMessageType.ACK : CoapMessageType.NON,
                        tokenLength,
                        CoapMessageCode.BAD_REQUEST,
                        messageId,
                        token,
                        null,
                        "Format-Error: OptionLength can not be 15!".getBytes(StandardCharsets.UTF_8),
                        packet.sender()
                );
                ctx.writeAndFlush(response);
                return;
            }

            if (in.readableBytes() < optionLength) {
                // Return 4.00 Response
                CoapMessage response = new CoapMessage(
                        VERSION,
                        type == CoapMessageType.CON.value() ? CoapMessageType.ACK : CoapMessageType.NON,
                        tokenLength,
                        CoapMessageCode.BAD_REQUEST,
                        messageId,
                        token,
                        null,
                        "Format-Error: The number of readable bytes is less than optionLength".getBytes(StandardCharsets.UTF_8),
                        packet.sender()
                );
                ctx.writeAndFlush(response);
                return;
            }
            byte[] optionValue = new byte[optionLength];
            in.readBytes(optionValue);

            options.add(new CoapMessageOption(optionNumber, optionValue));
        }

        // Handle payload
        byte[] payload = null;
        if (in.readableBytes() > 0) {
            payload = new byte[in.readableBytes()];
            in.readBytes(payload);
        }

        CoapMessage coapMessage = new CoapMessage(version, type, tokenLength, code, messageId, token, options, payload, packet.sender());
        out.add(coapMessage);
    }
}