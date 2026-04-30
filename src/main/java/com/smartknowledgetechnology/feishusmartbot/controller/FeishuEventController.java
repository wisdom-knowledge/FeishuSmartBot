package com.smartknowledgetechnology.feishusmartbot.controller;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.service.WelcomeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class FeishuEventController {

    private final WelcomeService welcomeService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(15);

    public FeishuEventController(WelcomeService welcomeService) {
        this.welcomeService = welcomeService;
    }

    @PostMapping("/feishu_callback")
    public Map<String, String> eventHandler(@RequestBody JSONObject eventJson) {
        System.out.println(">>> [DEBUG] 收到飞书回调: " + eventJson.toJSONString());

        if (eventJson.containsKey("challenge")) {
            Map<String, String> resp = new HashMap<>();
            resp.put("challenge", eventJson.getString("challenge"));
            return resp;
        }

        JSONObject header = eventJson.getJSONObject("header");
        if (header == null) return Map.of("msg", "ok");

        String eventType = header.getString("event_type");
        System.out.println(">>> [DEBUG] 事件类型: " + eventType);
        JSONObject event = eventJson.getJSONObject("event");

        if ("im.chat.member.user.added_v1".equals(eventType)) {
            String chatId = event.getString("chat_id");
            JSONArray users = event.getJSONArray("users");
            if (users != null && !users.isEmpty()) {
                String userId = users.getJSONObject(0).getJSONObject("user_id").getString("open_id");
                System.out.println(">>> 监测到新入群事件: Chat=" + chatId + ", User=" + userId);
                executorService.submit(() -> welcomeService.handleJoinEvent(chatId, userId));
            }
        } else if ("im.message.receive_v1".equals(eventType)) {
            JSONObject message = event.getJSONObject("message");
            String chatType = message.getString("chat_type");
            if ("p2p".equals(chatType)) {
                String userId = event.getJSONObject("sender").getJSONObject("sender_id").getString("open_id");
                String chatId = message.getString("chat_id");
                System.out.println(">>> 收到用户私信，准备自动回复: " + userId);
                executorService.submit(() -> welcomeService.handlePrivateMessage(userId, chatId));
            }
        }

        return Map.of("msg", "ok");
    }
}
