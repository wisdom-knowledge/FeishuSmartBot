package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FeishuMessageServiceTest {

    @Test
    void sendClaudeCodeFormalGroupWelcomeSendsEphemeralCardWithPrimaryBotButton() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuMessageService service = new FeishuMessageService(apiClient);

        service.sendClaudeCodeFormalGroupWelcome("oc_test_chat", "ou_test_user", "tenant-token");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).postRequestAndCheckSuccess(
                eq("https://open.feishu.cn/open-apis/ephemeral/v1/send"),
                bodyCaptor.capture(),
                eq("tenant-token"),
                eq("Claude Code 正式群欢迎临时卡片")
        );

        JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
        assertEquals("oc_test_chat", body.getString("chat_id"));
        assertEquals("ou_test_user", body.getString("open_id"));
        assertEquals("interactive", body.getString("msg_type"));

        JSONObject card = body.getJSONObject("card");
        assertEquals("orange", card.getJSONObject("header").getString("template"));

        JSONArray elements = card.getJSONArray("elements");
        JSONObject markdown = elements.getJSONObject(0);
        assertEquals("div", markdown.getString("tag"));
        assertEquals("lark_md", markdown.getJSONObject("text").getString("tag"));
        assertTrue(markdown.getJSONObject("text").getString("content").contains("<at id=ou_test_user></at>"));
        assertTrue(markdown.getJSONObject("text").getString("content").contains("恭喜您进入正式任务阶段"));

        JSONObject button = null;
        for (int i = 0; i < elements.size(); i++) {
            JSONObject element = elements.getJSONObject(i);
            if (!"action".equals(element.getString("tag"))) continue;
            JSONArray actions = element.getJSONArray("actions");
            if (actions == null || actions.isEmpty()) continue;
            JSONObject candidate = actions.getJSONObject(0);
            if ("打开任务助手".equals(candidate.getJSONObject("text").getString("content"))) {
                button = candidate;
                break;
            }
        }
        assertTrue(button != null, "should contain 打开任务助手 button");
        assertEquals("button", button.getString("tag"));
        assertEquals("primary", button.getString("type"));
        assertEquals("打开任务助手", button.getJSONObject("text").getString("content"));
        assertEquals(
                "https://applink.feishu.cn/client/bot/open?appId=cli_a909be3353badbc6",
                button.getString("url")
        );
    }

    @Test
    void sendPrivateGuideByProjectUsesProjectSpecificClaudeCodeTemplate() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuMessageService service = new FeishuMessageService(apiClient);

        service.sendPrivateGuideByProject("ou_test_user", "测试用户", "tenant-token", "claudecode_formal");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).postRequest(
                eq("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id"),
                bodyCaptor.capture(),
                eq("tenant-token")
        );

        JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
        assertEquals("interactive", body.getString("msg_type"));
        JSONObject card = JSONObject.parseObject(body.getString("content"));
        assertEquals("项目任务引导", card.getJSONObject("header")
                .getJSONObject("title").getString("content"));
        JSONArray elements = card.getJSONArray("elements");
        assertTrue(elements.getJSONObject(1).getJSONObject("text")
                .getString("content").contains("【Claude Code 正式项目】"));
        assertTrue(elements.getJSONObject(1).getJSONObject("text")
                .getString("content").contains("任务提交指南"));
    }

    @Test
    void sendPrivateGuideByProjectsCombinesMatchedProjectGuides() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuMessageService service = new FeishuMessageService(apiClient);

        service.sendPrivateGuideByProjects("ou_test_user", "测试用户", "tenant-token",
                List.of("claudecode_formal", "newbie"));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).postRequest(
                eq("https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id"),
                bodyCaptor.capture(),
                eq("tenant-token")
        );

        JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
        assertEquals("interactive", body.getString("msg_type"));
        JSONObject card = JSONObject.parseObject(body.getString("content"));
        JSONArray elements = card.getJSONArray("elements");
        assertTrue(elements.getJSONObject(1).getJSONObject("text")
                .getString("content").contains("【Claude Code 正式项目】"));
        assertTrue(elements.getJSONObject(3).getJSONObject("text")
                .getString("content").contains("【Hippo 新人项目】"));
        assertTrue(elements.getJSONObject(1).getJSONObject("text")
                .getString("content").contains("Claude Code 认证专家"));
        assertTrue(elements.getJSONObject(3).getJSONObject("text")
                .getString("content").contains("欢迎加入项目群"));
    }
}
