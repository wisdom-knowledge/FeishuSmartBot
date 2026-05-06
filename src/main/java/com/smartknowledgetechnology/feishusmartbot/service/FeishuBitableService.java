package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class FeishuBitableService {

    private static final String FIELD_OPEN_ID = "OpenID";
    private static final String FIELD_PRIVATE_STATUS = "私聊状态";
    private static final String FIELD_PRIVATE_TIME = "私聊时间";
    private static final String FIELD_PRIVATE_CHAT_ID = "私聊ChatID";

    private final FeishuApiClient apiClient;
    private final ChatGroupConfig chatGroupConfig;

    public FeishuBitableService(FeishuApiClient apiClient, ChatGroupConfig chatGroupConfig) {
        this.apiClient = apiClient;
        this.chatGroupConfig = chatGroupConfig;
    }

    public void writeToBitable(String name, String userId, String token, String tableId) {
        String url = "https://open.feishu.cn/open-apis/bitable/v1/apps/"
                + chatGroupConfig.getBitableAppToken() + "/tables/" + tableId + "/records";

        JSONObject fields = new JSONObject();
        fields.put("人员名称", name);
        fields.put("OpenID", userId);
        fields.put("入群时间", System.currentTimeMillis());

        JSONObject body = new JSONObject();
        body.put("fields", fields);

        System.out.println(">>> 写入多维表格: tableId=" + tableId + ", name=" + name + ", userId=" + userId);
        String resp = apiClient.postRequestWithResponse(url, body.toJSONString(), token);
        System.out.println(">>> 多维表格写入响应: " + resp);
    }

    public void trackClaudeCodePrivateChat(String userId, String privateChatId, String status, String token) {
        String tableId = chatGroupConfig.getBitableTableIdClaudeCodeFormal();
        if (tableId == null || tableId.trim().isEmpty()) {
            System.err.println(">>> Claude Code formal table id is empty; skip private chat status tracking");
            return;
        }
        System.out.println(">>> [ClaudePrivateTrack] tableId=" + tableId + ", userId=" + userId + ", status=" + status);

        JSONObject fields = new JSONObject();
        fields.put(FIELD_OPEN_ID, userId);
        fields.put(FIELD_PRIVATE_STATUS, status);
        fields.put(FIELD_PRIVATE_TIME, System.currentTimeMillis());
        fields.put(FIELD_PRIVATE_CHAT_ID, privateChatId);

        JSONObject body = new JSONObject();
        body.put("fields", fields);

        String recordId = findRecordIdByOpenId(tableId, userId, token);
        if (recordId == null) {
            // Fallback: fetch all fields in case field_names filter misses renamed/case-variant column titles.
            recordId = findRecordIdByAnyField(tableId, userId, token);
        }
        if (recordId == null) {
            System.out.println(">>> Claude Code private chat user is not in target table; skip userId=" + userId);
            return;
        }

        String updateUrl = "https://open.feishu.cn/open-apis/bitable/v1/apps/"
                + chatGroupConfig.getBitableAppToken() + "/tables/" + tableId + "/records/" + recordId;
        String resp = apiClient.patchRequestWithResponse(updateUrl, body.toJSONString(), token);
        System.out.println(">>> Updated Claude Code private chat tracking record: " + resp);
    }

    private String findRecordIdByOpenId(String tableId, String userId, String token) {
        String fieldNamesParam = java.net.URLEncoder.encode("[\"" + FIELD_OPEN_ID + "\"]", StandardCharsets.UTF_8);
        return findRecordId(tableId, userId, token, fieldNamesParam, true);
    }

    private String findRecordIdByAnyField(String tableId, String userId, String token) {
        return findRecordId(tableId, userId, token, null, false);
    }

    private String findRecordId(String tableId, String userId, String token, String fieldNamesParam, boolean strictOpenIdOnly) {
        String pageToken = "";
        boolean hasMore = true;
        int debugPrinted = 0;

        while (hasMore) {
            StringBuilder urlBuilder = new StringBuilder("https://open.feishu.cn/open-apis/bitable/v1/apps/")
                    .append(chatGroupConfig.getBitableAppToken())
                    .append("/tables/")
                    .append(tableId)
                    .append("/records?page_size=500");
            if (fieldNamesParam != null) {
                urlBuilder.append("&field_names=").append(fieldNamesParam);
            }
            if (!pageToken.isEmpty()) {
                urlBuilder.append("&page_token=").append(pageToken);
            }
            String url = urlBuilder.toString();

            try {
                ResponseEntity<JSONObject> resp = apiClient.getRequest(url, token);
                JSONObject body = resp.getBody();
                if (body == null) break;

                JSONObject data = body.getJSONObject("data");
                if (data == null) break;

                JSONArray items = data.getJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject record = items.getJSONObject(i);
                        JSONObject fields = record.getJSONObject("fields");
                        if (fields == null) continue;

                        if (strictOpenIdOnly) {
                            if (debugPrinted < 5) {
                                Object rawOpenId = fields.get(FIELD_OPEN_ID);
                                System.out.println(">>> [ClaudePrivateTrack] sampled OpenID raw=" + String.valueOf(rawOpenId)
                                        + ", class=" + (rawOpenId == null ? "null" : rawOpenId.getClass().getName()));
                                debugPrinted++;
                            }
                            if (fieldContainsId(fields.get(FIELD_OPEN_ID), userId)) {
                                return record.getString("record_id");
                            }
                        } else if (fieldsContainsId(fields, userId)) {
                            return record.getString("record_id");
                        }
                    }
                }

                pageToken = data.getString("page_token");
                if (pageToken == null) pageToken = "";
                hasMore = Boolean.TRUE.equals(data.getBoolean("has_more")) && !pageToken.isEmpty();
            } catch (Exception e) {
                System.err.println(">>> Failed to find Claude Code Bitable record: " + e.getMessage());
                break;
            }
        }
        return null;
    }

    private boolean fieldsContainsId(JSONObject fields, String expectedId) {
        for (String key : fields.keySet()) {
            Object value = fields.get(key);
            if (fieldContainsId(value, expectedId)) {
                return true;
            }
        }
        return false;
    }

    private boolean fieldContainsId(Object val, String expectedId) {
        if (val == null || expectedId == null) return false;

        if (val instanceof JSONObject obj) {
            return expectedId.equals(obj.getString("id"))
                    || expectedId.equals(obj.getString("open_id"))
                    || expectedId.equals(obj.getString("text"));
        }
        if (val instanceof Map<?, ?> map) {
            return expectedId.equals(stringValue(map.get("id")))
                    || expectedId.equals(stringValue(map.get("open_id")))
                    || expectedId.equals(stringValue(map.get("text")));
        }
        if (val instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                if (fieldContainsId(arr.get(i), expectedId)) return true;
            }
            return false;
        }
        if (val instanceof Iterable<?> items) {
            for (Object item : items) {
                if (fieldContainsId(item, expectedId)) return true;
            }
            return false;
        }
        return expectedId.equals(val.toString().trim());
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    /**
     * 从多维表格读取已提交人员的标识集合（使用默认 appToken）
     */
    public Set<String> getSubmittedIds(String tableId, String fieldName, String token) {
        return getSubmittedIds(chatGroupConfig.getBitableAppToken(), tableId, fieldName, token);
    }

    /**
     * 从多维表格读取已提交人员的标识集合（支持自定义 appToken）。
     * 自动处理飞书"人员"类型字段（提取其中的 id/open_id），
     * 同时兼容纯文本字段。
     */
    public Set<String> getSubmittedIds(String appToken, String tableId, String fieldName, String token) {
        Set<String> ids = new HashSet<>();
        String pageToken = "";
        boolean hasMore = true;

        while (hasMore) {
            String url = "https://open.feishu.cn/open-apis/bitable/v1/apps/"
                    + appToken + "/tables/" + tableId + "/records?page_size=500"
                    + "&field_names=" + java.net.URLEncoder.encode("[\"" + fieldName + "\"]", StandardCharsets.UTF_8)
                    + (pageToken.isEmpty() ? "" : "&page_token=" + pageToken);

            try {
                ResponseEntity<JSONObject> resp = apiClient.getRequest(url, token);
                JSONObject body = resp.getBody();
                if (body == null) break;

                JSONObject data = body.getJSONObject("data");
                if (data == null) break;

                JSONArray items = data.getJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject record = items.getJSONObject(i);
                        JSONObject fields = record.getJSONObject("fields");
                        if (fields != null) {
                            Object val = fields.get(fieldName);
                            extractIds(val, ids);
                        }
                    }
                }

                pageToken = data.getString("page_token");
                if (pageToken == null) pageToken = "";
                hasMore = Boolean.TRUE.equals(data.getBoolean("has_more")) && !pageToken.isEmpty();
            } catch (Exception e) {
                System.err.println(">>> [催促] 读取多维表格失败: " + e.getMessage());
                break;
            }
        }
        return ids;
    }

    /**
     * 从字段值中提取用户标识。
     * 飞书"人员"类型字段返回 JSONObject（含 id/name）或 JSONArray，
     * 纯文本字段直接取字符串值。
     */
    private void extractIds(Object val, Set<String> ids) {
        if (val == null) return;

        if (val instanceof JSONObject obj) {
            String id = obj.getString("id");
            if (id != null && !id.trim().isEmpty()) {
                ids.add(id.trim());
            }
        } else if (val instanceof JSONArray arr) {
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (item instanceof JSONObject obj) {
                    String id = obj.getString("id");
                    if (id != null && !id.trim().isEmpty()) {
                        ids.add(id.trim());
                    }
                } else if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) ids.add(s);
                }
            }
        } else {
            String strVal = val.toString().trim();
            if (!strVal.isEmpty()) {
                ids.add(strVal);
            }
        }
    }
}
