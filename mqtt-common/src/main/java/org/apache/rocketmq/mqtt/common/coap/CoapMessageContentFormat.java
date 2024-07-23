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

public enum CoapMessageContentFormat {
    TEXT_PLAIN(0),
//    APPLICATION_LINK(40),
//    APPLICATION_XML(41),
//    APPLICATION_OCTET_STREAM(42),
//    APPLICATION_EXI(47),
    APPLICATION_JSON(50);

    private static final CoapMessageContentFormat[] VALUES;
    private final int value;

    private CoapMessageContentFormat(int value) { this.value = value; }

    public int value() { return this.value; }

    public static CoapMessageContentFormat valueOf(int number) {
        if (number >= 0 && number < VALUES.length && VALUES[number] != null) {
            return  VALUES[number];
        } else {
            throw new IllegalArgumentException("Unknown CoapMessageContentFormat " + number);
        }
    }

    public byte[] toByteArray() {
        if (value < 0 || value > 65535) {
            throw new IllegalArgumentException("Value out of range for 0-2 byte representation.");
        }
        if (value <= 255) {
            // Use 1 byte if the value can fit into 1 byte
            return new byte[]{(byte) value};
        } else {
            // Use 2 bytes if the value needs more than 1 byte
            return new byte[]{(byte) (value >> 8), (byte) value};
        }
    }


    static {
        CoapMessageContentFormat[] values = values();
        VALUES = new CoapMessageContentFormat[51];

        for (CoapMessageContentFormat number : values) {
            int value = number.value;
            if (VALUES[value] != null) {
                throw new AssertionError("Value already in use: " + value + " by " + VALUES[value]);
            }
            VALUES[value] = number;
        }
    }
}
