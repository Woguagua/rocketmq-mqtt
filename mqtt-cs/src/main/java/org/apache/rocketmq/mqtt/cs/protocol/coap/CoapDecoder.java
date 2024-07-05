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
import org.apache.rocketmq.mqtt.common.coap.*;
import org.apache.rocketmq.mqtt.common.model.Constants;


import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// todo: Dealing with messageID and token when the request is wrong.
// todo: Dealing with exception throwing when the request is wrong.
public class CoapDecoder extends MessageToMessageDecoder<DatagramPacket> {

    private CoapMessageType coapType;
    private int coapTokenLength;
    private CoapMessageCode coapCode;
    private int coapMessageId;
    private byte[] coapToken;
    private List<CoapMessageOption> coapOptions;
    private byte[] coapPayload;
    InetSocketAddress remoteAddress;

    private String errorContent;
    private CoapMessageCode errorCode;

    @Override
    public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) {

        ByteBuf in = packet.content();
        remoteAddress = packet.sender();

        // The length of Coap message is at least 4 bytes.
        if (in.readableBytes() < 4) {
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: The length of header must be at least 4 bytes!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }

        // Handle first byte, including version, type, and token length.
        int firstByte = in.readUnsignedByte();
        int version = (firstByte >> 6) & 0x03;
        if (version != Constants.COAP_VERSION) {
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: Version must be 1!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        coapType = CoapMessageType.valueOf((firstByte >> 4) & 0x03);
        coapTokenLength = firstByte & 0x0F;
        if (coapTokenLength > Constants.COAP_MAX_TOKEN_LENGTH) {
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: The length of token is too long!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }

        // Handle code
        try {
            coapCode = CoapMessageCode.valueOf(in.readUnsignedByte());
        } catch (IllegalArgumentException e) {
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: The code is not defined!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        if (!CoapMessageCode.isRequestCode(coapCode)) {
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: The code must be a request code!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }

        // Handle messageID
        coapMessageId = in.readUnsignedShort();

        // Handle token
        if (in.readableBytes() < coapTokenLength) {
            // Return 4.00 Response
            errorCode = CoapMessageCode.BAD_REQUEST;
            errorContent = "Format-Error: The length of remaining readable bytes is less than tokenLength!";
            sendErrorResponse(ctx);
            // Skip unread bytes
            in.skipBytes(in.readableBytes());
            return;
        }
        coapToken = new byte[coapTokenLength];
        in.readBytes(coapToken);

        CoapRequestMessage coapMessage = new CoapRequestMessage(version, coapType, coapTokenLength, coapCode, coapMessageId, coapToken, remoteAddress);

        // Handle options
        int nextByte;
        int optionNumber = 0;
        coapOptions = new ArrayList<>();
        StringBuilder uriSb = new StringBuilder();
        while (in.readableBytes() > 0) {

            nextByte = in.readUnsignedByte();
            if (nextByte == Constants.COAP_PAYLOAD_MARKER) {
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
                errorCode = CoapMessageCode.BAD_REQUEST;
                errorContent = "Format-Error: OptionDelta can not be 15!";
                sendErrorResponse(ctx);
                in.skipBytes(in.readableBytes());
                return;
            }

            optionNumber += optionDelta;    // current optionNumber = last optionNumber + optionDelta

            if (!CoapMessageOptionNumber.isValid(optionNumber)) {
                // Return 4.02 Response
                errorCode = CoapMessageCode.BAD_OPTION;
                errorContent = "Format-Error: Option number is not defined!";
                sendErrorResponse(ctx);
                in.skipBytes(in.readableBytes());
                return;
            }

            if (optionLength == 13) {
                optionLength += in.readUnsignedByte();
            } else if (optionLength == 14) {
                optionLength += 255 + in.readUnsignedShort();
            } else if (optionLength == 15) {
                // Return 4.00 Response
                errorCode = CoapMessageCode.BAD_REQUEST;
                errorContent = "Format-Error: OptionLength can not be 15!";
                sendErrorResponse(ctx);
                in.skipBytes(in.readableBytes());
                return;
            }

            if (in.readableBytes() < optionLength) {
                // Return 4.00 Response
                errorCode = CoapMessageCode.BAD_REQUEST;
                errorContent = "Format-Error: The number of readable bytes is less than optionLength";
                sendErrorResponse(ctx);
                in.skipBytes(in.readableBytes());
                return;
            }
            byte[] optionValue = new byte[optionLength];
            in.readBytes(optionValue);

            if (optionNumber == CoapMessageOptionNumber.URI_PATH.value()) {
                uriSb.append(new String(optionValue, StandardCharsets.UTF_8));
                uriSb.append(Constants.COAP_URI_DELIMITER);
            }

            coapOptions.add(new CoapMessageOption(optionNumber, optionValue));
        }
        if (!coapOptions.isEmpty()) {
            coapMessage.setOptions(coapOptions);
            if (uriSb.length() > 0) {
                coapMessage.setUriPath(uriSb.toString());
            }
        }

        // Handle payload
        if (in.readableBytes() > 0) {
            coapPayload = new byte[in.readableBytes()];
            in.readBytes(coapPayload);
            coapMessage.setPayload(coapPayload);
        }

//        sendTestResponse(ctx);
        out.add(coapMessage);
    }

    public void sendErrorResponse(ChannelHandlerContext ctx) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                coapType == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                coapToken == null ? 0 : coapTokenLength,
                errorCode,
                coapMessageId,
                coapToken,
                null,
                errorContent.getBytes(StandardCharsets.UTF_8),
                remoteAddress
        );
        ctx.writeAndFlush(response);
    }

    public void sendTestResponse(ChannelHandlerContext ctx) {
        CoapMessage response = new CoapMessage(
                Constants.COAP_VERSION,
                coapType == CoapMessageType.CON ? CoapMessageType.ACK : CoapMessageType.NON,
                coapToken == null ? 0 : coapTokenLength,
                CoapMessageCode.Valid,
                coapMessageId,
                coapToken,
                null,
                "Hello, I have accept your request successfully!".getBytes(StandardCharsets.UTF_8),
                remoteAddress
        );
        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(response);
        } else {
            System.out.println("Channel is not active");
        }

    }
}