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
package org.apache.rocketmq.mqtt.cs.config;

import java.util.EnumSet;

public class CoapConf {
    /** Coap Version */
    public static final int VERSION = 1;

    /** Coap Division Marker for Payload */
    public static final int PAYLOAD_MARKER = 0xFF;

    /** Coap Token Length must be 0~8 */
    public static final int MAX_TOKEN_LENGTH = 8;

    /**
     * Coap Type,
     * Four types: CON, NON, ACK, RST
     */
    public enum Type {
        CON(0),
        NON(1),
        ACK(2),
        RST(3);

        private final int value;

        Type(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Coap Request Code for 0.xx,
     * Four types: GET, POST, PUT, DELETE
     */
    public enum RequestCode {
        GET(1),
        POST(2),
        PUT(3),
        DELETE(4);

        private final int value;

        RequestCode(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Coap Response Code for 4.xx Client Error:
     * 4.00: Bad Request, 4.01: Unauthorized, 4.02: Bad Option, 4.03: Forbidden, 4.04: Not Found, 4.05: Method Not Allowed, 4.06: Not Acceptable, 4.12: Precondition Failed, 4.13: Request Entity Too Large, 4.15: Unsupported Content-Format
     */
    public enum ResponseCodeClientError {
        BAD_REQUEST(128),
        UNAUTHORIZED(129),
        BAD_OPTION(130),
        FORBIDDEN(131),
        NOT_FOUND(132),
        METHOD_NOT_ALLOWED(133),
        NOT_ACCEPTABLE(134),
        PRECONDITION_FAILED(140),
        REQUEST_ENTITY_TOO_LARGE(141),
        UNSUPPORTED_CONTENT_FORMAT(143);

        private final int value;

        ResponseCodeClientError(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Coap Response Code for 5.xx Server Error:
     * 5.00: Internal Server Error, 5.01: Not Implemented, 5.02: Bad Gateway, 5.03: Service Unavailable, 5.04: Gateway Timeout, 5.05: Proxying Not Supported
     */
    public enum ResponseCodeServerError {
        INTERNAL_SERVER_ERROR(160),
        NOT_IMPLEMENTED(161),
        BAD_GATEWAY(162),
        SERVICE_UNAVAILABLE(163),
        GATEWAY_TIMEOUT(164),
        PROXYING_NOT_SUPPORTED(165);

        private final int value;

        ResponseCodeServerError(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Coap Option Number
     */
    public enum OptionNumber {
        IF_MATCH(1),
        URI_HOST(3),
        ETAG(4),
        IF_NONE_MATCH(5),
        OBSERVE(6),
        URI_PORT(7),
        LOCATION_PATH(8),
        URI_PATH(11),
        CONTENT_FORMAT(12),
        MAX_AGE(14),
        URI_QUERY(15),
        ACCEPT(17),
        LOCATION_QUERY(20),
        BLOCK_2(23),
        BLOCK_1(27),
        SIZE_2(28),
        PROXY_URI(35),
        PROXY_SCHEME(39),
        SIZE_1(60);

        private final int value;

        OptionNumber(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static boolean isValid(int value) {
            return EnumSet.allOf(OptionNumber.class).stream().anyMatch(option -> option.getValue() == value);
        }
    }

}