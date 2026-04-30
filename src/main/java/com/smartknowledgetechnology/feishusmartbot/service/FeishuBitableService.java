package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Service
public class FeishuBitableService {

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
