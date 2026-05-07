package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeishuBitableServiceTest {

    @Test
    void trackClaudeCodePrivateChatUpdatesExistingRecordByOpenId() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");

        JSONObject fields = new JSONObject();
        fields.put("OpenID", "ou_test_user");

        JSONObject record = new JSONObject();
        record.put("record_id", "rec_test");
        record.put("fields", fields);

        JSONArray items = new JSONArray();
        items.add(record);

        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("has_more", false);

        JSONObject response = new JSONObject();
        response.put("data", data);

        when(apiClient.getRequest(contains("/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(response));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        service.trackClaudeCodePrivateChat("ou_test_user", "oc_p2p_chat", "已打开会话", "tenant-token");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).patchRequestWithResponse(
                contains("/tables/tbl_claude/records/rec_test"),
                bodyCaptor.capture(),
                eq("tenant-token")
        );

        JSONObject body = JSONObject.parseObject(bodyCaptor.getValue());
        JSONObject updatedFields = body.getJSONObject("fields");
        assertEquals("已打开会话", updatedFields.getString("私聊状态"));
        assertEquals("oc_p2p_chat", updatedFields.getString("私聊ChatID"));
        assertTrue(updatedFields.containsKey("私聊时间"));
    }

    @Test
    void trackClaudeCodePrivateChatMatchesBitableRichTextOpenId() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");

        JSONObject textNode = new JSONObject();
        textNode.put("type", "text");
        textNode.put("text", "ou_b402e215ef86d638cd5cb8dd2d73386a");

        JSONArray openIdValue = new JSONArray();
        openIdValue.add(textNode);

        JSONObject fields = new JSONObject();
        fields.put("OpenID", openIdValue);

        JSONObject record = new JSONObject();
        record.put("record_id", "rec_rich_text");
        record.put("fields", fields);

        JSONArray items = new JSONArray();
        items.add(record);

        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("has_more", false);

        JSONObject response = new JSONObject();
        response.put("data", data);

        when(apiClient.getRequest(contains("/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(response));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        service.trackClaudeCodePrivateChat(
                "ou_b402e215ef86d638cd5cb8dd2d73386a",
                "oc_p2p_chat",
                "已发送私信",
                "tenant-token"
        );

        verify(apiClient).patchRequestWithResponse(
                contains("/tables/tbl_claude/records/rec_rich_text"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("tenant-token")
        );
    }

    @Test
    void trackClaudeCodePrivateChatMatchesJacksonDeserializedRichTextOpenId() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");

        JSONObject fields = new JSONObject();
        fields.put("OpenID", List.of(Map.of(
                "type", "text",
                "text", "ou_b402e215ef86d638cd5cb8dd2d73386a"
        )));

        JSONObject record = new JSONObject();
        record.put("record_id", "rec_jackson_text");
        record.put("fields", fields);

        JSONArray items = new JSONArray();
        items.add(record);

        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("has_more", false);

        JSONObject response = new JSONObject();
        response.put("data", data);

        when(apiClient.getRequest(contains("/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(response));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        service.trackClaudeCodePrivateChat(
                "ou_b402e215ef86d638cd5cb8dd2d73386a",
                "oc_p2p_chat",
                "已发送私信",
                "tenant-token"
        );

        verify(apiClient).patchRequestWithResponse(
                contains("/tables/tbl_claude/records/rec_jackson_text"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("tenant-token")
        );
    }


    @Test
    void trackClaudeCodePrivateChatSkipsUsersMissingFromTargetTable() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");

        JSONObject data = new JSONObject();
        data.put("items", new JSONArray());
        data.put("has_more", false);

        JSONObject response = new JSONObject();
        response.put("data", data);

        when(apiClient.getRequest(contains("/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(response));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        service.trackClaudeCodePrivateChat("ou_other_user", "oc_p2p_chat", "已打开会话", "tenant-token");

        verify(apiClient, never()).patchRequestWithResponse(
                contains("/tables/tbl_claude/records/"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("tenant-token")
        );
        verify(apiClient, never()).postRequestWithResponse(
                contains("/tables/tbl_claude/records"),
                org.mockito.ArgumentMatchers.anyString(),
                eq("tenant-token")
        );
    }

    @Test
    void resolveProjectTypeByOpenIdReturnsMatchedProject() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentFormal", "tbl_coding_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentTrial", "tbl_coding_trial");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Formal", "tbl_hippo_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Exam", "tbl_hippo_exam");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdMcpTrial", "tbl_mcp");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdNewbie", "tbl_newbie");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdTrial", "tbl_trial");

        when(apiClient.getRequest(contains("/tables/tbl_claude/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_coding_formal/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(recordsOf("rec_coding", "ou_test_user"))));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        String projectType = service.resolveProjectTypeByOpenId("ou_test_user", "tenant-token");

        assertEquals("coding_agent_formal", projectType);
    }

    @Test
    void resolveProjectTypeByOpenIdReturnsNullWhenNoTableMatches() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentFormal", "tbl_coding_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentTrial", "tbl_coding_trial");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Formal", "tbl_hippo_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Exam", "tbl_hippo_exam");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdMcpTrial", "tbl_mcp");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdNewbie", "tbl_newbie");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdTrial", "tbl_trial");

        when(apiClient.getRequest(contains("/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        String projectType = service.resolveProjectTypeByOpenId("ou_no_match", "tenant-token");

        assertEquals(null, projectType);
    }

    @Test
    void resolveProjectTypesByOpenIdReturnsAllMatchedProjects() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        ChatGroupConfig chatGroupConfig = new ChatGroupConfig();
        ReflectionTestUtils.setField(chatGroupConfig, "bitableAppToken", "app_token");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdClaudeCodeFormal", "tbl_claude");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentFormal", "tbl_coding_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdCodingAgentTrial", "tbl_coding_trial");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Formal", "tbl_hippo_formal");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdHippo3Exam", "tbl_hippo_exam");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdMcpTrial", "tbl_mcp");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdNewbie", "tbl_newbie");
        ReflectionTestUtils.setField(chatGroupConfig, "bitableTableIdTrial", "tbl_trial");

        when(apiClient.getRequest(contains("/tables/tbl_claude/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(recordsOf("rec_claude", "ou_test_user"))));
        when(apiClient.getRequest(contains("/tables/tbl_coding_formal/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_coding_trial/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_hippo_formal/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_hippo_exam/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_mcp/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));
        when(apiClient.getRequest(contains("/tables/tbl_newbie/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(recordsOf("rec_newbie", "ou_test_user"))));
        when(apiClient.getRequest(contains("/tables/tbl_trial/records?page_size=500"), eq("tenant-token")))
                .thenReturn(ResponseEntity.ok(buildRecordsResponse(new JSONArray())));

        FeishuBitableService service = new FeishuBitableService(apiClient, chatGroupConfig);

        List<String> projectTypes = service.resolveProjectTypesByOpenId("ou_test_user", "tenant-token");

        assertEquals(List.of("claudecode_formal", "newbie"), projectTypes);
    }

    private JSONObject buildRecordsResponse(JSONArray items) {
        JSONObject data = new JSONObject();
        data.put("items", items);
        data.put("has_more", false);
        data.put("page_token", "");
        JSONObject body = new JSONObject();
        body.put("data", data);
        return body;
    }

    private JSONArray recordsOf(String recordId, String openId) {
        JSONObject fields = new JSONObject();
        fields.put("OpenID", openId);
        JSONObject record = new JSONObject();
        record.put("record_id", recordId);
        record.put("fields", fields);
        JSONArray items = new JSONArray();
        items.add(record);
        return items;
    }
}
