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
    void sendClaudeCodeFormalGroupWelcomeSendsEphemeralCardWithoutAssistantButton() {
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
        String topContent = markdown.getJSONObject("text").getString("content");
        assertTrue(topContent.contains("<at id=ou_test_user></at>"));
        assertTrue(topContent.contains("欢迎加入「XX 专家认证项目」"));
        assertTrue(topContent.contains("请在进群后一周内完成任务提交"));
        assertTrue(topContent.contains("提交入口"));
        assertTrue(topContent.contains("一面千识人才平台"));
        assertTrue(topContent.contains("任务SOP文档"));
        assertTrue(topContent.contains("项目流程"));
        assertTrue(!topContent.contains("打开任务助手"));
        assertEquals(1, elements.size());
    }

    @Test
    void sendCompassFormalGroupWelcomeSendsEphemeralCardWithNoticeContent() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuMessageService service = new FeishuMessageService(apiClient);

        service.sendCompassFormalGroupWelcome("oc_test_chat", "ou_test_user", "tenant-token");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).postRequestAndCheckSuccess(
                eq("https://open.feishu.cn/open-apis/ephemeral/v1/send"),
                bodyCaptor.capture(),
                eq("tenant-token"),
                eq("Compass 正式群欢迎临时卡片")
        );

        JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
        assertEquals("oc_test_chat", body.getString("chat_id"));
        assertEquals("ou_test_user", body.getString("open_id"));
        assertEquals("interactive", body.getString("msg_type"));

        JSONObject card = body.getJSONObject("card");
        assertEquals("orange", card.getJSONObject("header").getString("template"));
        assertEquals("Compass 正式专家群入群通知", card.getJSONObject("header")
                .getJSONObject("title").getString("content"));

        JSONArray elements = card.getJSONArray("elements");
        JSONObject markdown = elements.getJSONObject(0);
        String content = markdown.getJSONObject("text").getString("content");
        assertTrue(content.contains("<at id=ou_test_user></at>"));
        assertTrue(content.contains("恭喜您进入Compass正式作业阶段"));
        assertTrue(content.contains("请勿申请Compass表格权限"));
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
