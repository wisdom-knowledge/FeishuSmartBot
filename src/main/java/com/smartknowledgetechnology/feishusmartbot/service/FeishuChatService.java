package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FeishuChatService {

    private final FeishuApiClient apiClient;

    public FeishuChatService(FeishuApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * 获取群内全部成员（排除机器人），返回 Map<open_id, name>
     */
    public Map<String, String> getAllChatMembers(String chatId, String token) {
        Map<String, String> members = new LinkedHashMap<>();
        String pageToken = "";
        boolean hasMore = true;

        while (hasMore) {
            String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId
                    + "/members?member_id_type=open_id&page_size=100"
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
                        JSONObject member = items.getJSONObject(i);
                        String memberId = member.getString("member_id");
                        String name = member.getString("name");
                        if (memberId != null && !memberId.isEmpty()) {
                            members.put(memberId, name != null ? name : "未知");
                        }
                    }
                }

                pageToken = data.getString("page_token");
                if (pageToken == null) pageToken = "";
                hasMore = Boolean.TRUE.equals(data.getBoolean("has_more")) && !pageToken.isEmpty();
            } catch (Exception e) {
                System.err.println("获取群成员失败: " + e.getMessage());
                break;
            }
        }
        return members;
    }

    /**
     * 获取用户名，三级降级策略：群成员列表 → 通讯录 → 兜底
     */
    public String getUserName(String chatId, String userId, String token) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}

        // 从群成员列表获取（支持外部人员）
        try {
            String pageToken = "";
            boolean hasMore = true;

            while (hasMore) {
                String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId
                        + "/members?member_id_type=open_id&page_size=100"
                        + (pageToken.isEmpty() ? "" : "&page_token=" + pageToken);

                ResponseEntity<JSONObject> resp = apiClient.getRequest(url, token);
                JSONObject body = resp.getBody();
                if (body == null) break;

                JSONObject data = body.getJSONObject("data");
                if (data == null) break;

                JSONArray items = data.getJSONArray("items");
                if (items != null) {
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject member = items.getJSONObject(i);
                        if (userId.equals(member.getString("member_id"))) {
                            String realName = member.getString("name");
                            if (realName != null && !realName.trim().isEmpty()) {
                                return realName.trim();
                            }
                        }
                    }
                }

                pageToken = data.getString("page_token");
                if (pageToken == null) pageToken = "";
                hasMore = Boolean.TRUE.equals(data.getBoolean("has_more")) && !pageToken.isEmpty();
            }
        } catch (Exception e) {
            System.err.println("从群接口获取昵称失败: " + e.getMessage());
        }

        // 重试通讯录接口（仅内部人员有效）
        try {
            String contactUrl = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
            ResponseEntity<JSONObject> resp = apiClient.getRequest(contactUrl, token);
            String realName = resp.getBody().getJSONObject("data").getJSONObject("user").getString("name");
            if (realName != null && !realName.trim().isEmpty()) {
                return realName.trim();
            }
        } catch (Exception e) {
            System.err.println("从通讯录接口获取昵称失败: " + e.getMessage());
        }

        return "未获取到用户名";
    }
}
