package com.smartknowledgetechnology.feishusmartbot.controller;

import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.service.WelcomeService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class FeishuEventControllerTest {

    @Test
    void eventHandlerDispatchesP2pChatCreateEvent() {
        WelcomeService welcomeService = mock(WelcomeService.class);
        FeishuEventController controller = new FeishuEventController(welcomeService);

        JSONObject user = new JSONObject();
        user.put("open_id", "ou_test_user");

        JSONObject event = new JSONObject();
        event.put("type", "p2p_chat_create");
        event.put("chat_id", "oc_p2p_chat");
        event.put("user", user);

        JSONObject payload = new JSONObject();
        payload.put("type", "event_callback");
        payload.put("event", event);

        controller.eventHandler(payload);

        verify(welcomeService, timeout(1000))
                .handleBotP2pChatCreated("ou_test_user", "oc_p2p_chat");
    }
}
