package org.nhnacademy.book2onandonbookservice.client;

import org.nhnacademy.book2onandonbookservice.dto.dooray.DoorayMessagePayload;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name="doorayHookClient", url = "${dooray.url}")
public interface DoorayHookClient {
    @PostMapping("/{serviceId}/{botId}/{botToken}")
    String sendMessage(@PathVariable("serviceId")String serviceId,
                       @PathVariable("botId") String botId,
                       @PathVariable("botToken") String botToken,
                       @RequestBody DoorayMessagePayload payload);
}