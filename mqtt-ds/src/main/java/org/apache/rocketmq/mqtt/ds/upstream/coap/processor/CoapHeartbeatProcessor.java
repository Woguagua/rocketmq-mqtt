package org.apache.rocketmq.mqtt.ds.upstream.coap.processor;

import org.apache.rocketmq.mqtt.common.hook.HookResult;
import org.apache.rocketmq.mqtt.common.model.CoapRequestMessage;
import org.apache.rocketmq.mqtt.ds.upstream.coap.CoapUpstreamProcessor;

import java.util.concurrent.CompletableFuture;

public class CoapHeartbeatProcessor implements CoapUpstreamProcessor {
    @Override
    public CompletableFuture<HookResult> process(CoapRequestMessage msg) {
        // todo: RPC broadcast (refresh token alive time)
        return null;
    }
}
