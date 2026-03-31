package com.smartknowledgetechnology.feishusmartbot;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class FeishuBotController {

    @Value("${feishu.app.id}")
    private String appId;

    @Value("${feishu.app.secret}")
    private String appSecret;

    @Value("${feishu.bitable.app-token}")
    private String bitableAppToken;

    @Value("${feishu.bitable.table-id.trial}")
    private String bitableTableIdTrial;

    @Value("${feishu.bitable.table-id.newbie}")
    private String bitableTableIdNewbie;

    @Value("${feishu.bitable.table-id.mcp.trial}")
    private String bitableTableIdMcpTrial;

    @Value("${feishu.chat.target-id.trial}")
    private String targetChatIdTrial;

    @Value("${feishu.chat.target-id.newbie}")
    private String targetChatIdNewbie;

    @Value("${feishu.chat.target-id.mcp.trial}")
    private String targetChatIdMcpTrial;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ExecutorService executorService = Executors.newFixedThreadPool(15);

    @PostMapping("/feishu_callback")
    public Map<String, String> eventHandler(@RequestBody JSONObject eventJson) {
        if (eventJson.containsKey("challenge")) {
            Map<String, String> resp = new HashMap<>();
            resp.put("challenge", eventJson.getString("challenge"));
            return resp;
        }

        JSONObject header = eventJson.getJSONObject("header");
        if (header == null) return Map.of("msg", "ok");

        String eventType = header.getString("event_type");
        JSONObject event = eventJson.getJSONObject("event");

        if ("im.chat.member.user.added_v1".equals(eventType)) {
            String chatId = event.getString("chat_id");
            JSONArray users = event.getJSONArray("users");
            if (users != null && !users.isEmpty()) {
                String userId = users.getJSONObject(0).getJSONObject("user_id").getString("open_id");
                System.out.println(">>> 监测到新入群事件: Chat=" + chatId + ", User=" + userId);
                executorService.submit(() -> handleJoinLogic(chatId, userId));
            }
        } else if ("im.message.receive_v1".equals(eventType)) {
            JSONObject message = event.getJSONObject("message");
            String chatType = message.getString("chat_type");
            if ("p2p".equals(chatType)) {
                String userId = event.getJSONObject("sender").getJSONObject("sender_id").getString("open_id");
                String chatId = message.getString("chat_id");
                System.out.println(">>> 收到用户私信，准备自动回复: " + userId);
                executorService.submit(() -> replyGuideToUser(userId, chatId));
            }
        }

        return Map.of("msg", "ok");
    }

    private void handleJoinLogic(String chatId, String userId) {
        try {
            String token = getTenantAccessToken();
            String userName = getUserName(chatId, userId, token);

            if (targetChatIdTrial.equals(chatId)) {
                writeToBitable(userName, userId, token, bitableTableIdTrial);
                sendTrialGroupWelcome(chatId, userId, token);
            } else if (targetChatIdNewbie.equals(chatId)) {
                writeToBitable(userName, userId, token, bitableTableIdNewbie);
                sendNewbieNotice(chatId, userId, userName, token);
            } else if (targetChatIdMcpTrial.equals(chatId)) {
                writeToBitable(userName, userId, token, bitableTableIdMcpTrial);
                sendMcpTrialGroupWelcome(chatId, userId, token);
            }
        } catch (Exception e) {
            System.err.println("处理业务失败: " + e.getMessage());
        }
    }

    // 优化后的getUserName方法

    private String getUserName(String chatId, String userId, String token) {
        // 1. 加短暂延迟，等飞书服务端同步新成员数据
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {}

        // 2. 从群成员列表获取（支持外部人员），加分页遍历
        try {
            String pageToken = "";
            boolean hasMore = true;

            while (hasMore) {
                String url = "https://open.feishu.cn/open-apis/im/v1/chats/" + chatId
                        + "/members?member_id_type=open_id&page_size=100"
                        + (pageToken.isEmpty() ? "" : "&page_token=" + pageToken);

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + token);

                ResponseEntity<JSONObject> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
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

                // 检查是否有下一页
                hasMore = Boolean.TRUE.equals(data.getBoolean("has_more"));
                pageToken = data.getString("page_token");
                if (pageToken == null || pageToken.isEmpty()) {
                    hasMore = false;
                }
            }
        } catch (Exception e) {
            System.err.println("从群接口获取昵称失败: " + e.getMessage());
        }

        // 3. 重试通讯录接口（仅内部人员有效）
        try {
            String contactUrl = "https://open.feishu.cn/open-apis/contact/v3/users/" + userId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + token);

            ResponseEntity<JSONObject> resp = restTemplate.exchange(contactUrl, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
            String realName = resp.getBody().getJSONObject("data").getJSONObject("user").getString("name");
            if (realName != null && !realName.trim().isEmpty()) {
                return realName.trim();
            }
        } catch (Exception e) {
            System.err.println("从通讯录接口获取昵称失败: " + e.getMessage());
        }

        // 4. 兜底
        return "未获取到用户名";
    }
    private void replyGuideToUser(String userId, String chatId) {
        try {
            String token = getTenantAccessToken();
            String userName = getUserName(chatId, userId, token);

            String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id";
            String privateContent =
                    "你好 " + userName + "，欢迎加入新人群！\n\n" +
                            "🎉 新人须知：\n" +
                            "1. 请收看群公告" +
                            "2. 查阅群公告中的《Hippo项目新人指南》\n" +
                            "3. 根据指南参加考试，通过进入试标\n"+
                            "4. 有问题可@管理员";

            JSONObject content = new JSONObject();
            content.put("text", privateContent);

            JSONObject body = new JSONObject();
            body.put("receive_id", userId);
            body.put("msg_type", "text");
            body.put("content", content.toJSONString());

            postRequest(url, body.toJSONString(), token);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeToBitable(String name, String userId, String token, String tableId) {
        String url = "https://open.feishu.cn/open-apis/bitable/v1/apps/" + bitableAppToken + "/tables/" + tableId + "/records";

        JSONObject fields = new JSONObject();
        fields.put("人员名称", name);
        fields.put("OpenID", userId);
        fields.put("入群时间", System.currentTimeMillis());

        JSONObject body = new JSONObject();
        body.put("fields", fields);

        postRequest(url, body.toJSONString(), token);
    }

    private void sendTrialGroupWelcome(String chatId, String userId, String token) {
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
        String welcomeContent =
                "<at user_id=\"" + userId + "\"></at> 欢迎通过面试，进入试标阶段，请务必阅读以下内容：\n" +
                        "1.进入试标群并查看群公告\n" +
                        "2.仔细阅读《Hippo项目指南》：\n" +
                        "https://meetchances.feishu.cn/docx/QJdZd5MjxoD2YJxOiZcc6tAFnBe";

        JSONObject content = new JSONObject();
        content.put("text", welcomeContent);

        JSONObject body = new JSONObject();
        body.put("receive_id", chatId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        postRequest(url, body.toJSONString(), token);
    }

    private void sendNewbieNotice(String chatId, String userId, String userName, String token) {
        String privateUrl = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id";
        String privateContent =
                "你好 " + userName + "，欢迎加入新人群！\n\n" +
                        "🎉 新人须知：\n" +
                        "1. 请收看群公告\n" +
                        "2. 查阅群公告中的《Hippo项目新人指南》\n" +
                        "3. 有问题可@群管理员咨询";
        JSONObject content = new JSONObject();
        content.put("text", privateContent);

        JSONObject body = new JSONObject();
        body.put("receive_id", userId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        try {
            postRequest(privateUrl, body.toJSONString(), token);
        } catch (Exception e) {
            String groupUrl = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
            String groupTxt = "<at user_id=\"" + userId + "\"></at> 你好，欢迎加入 Hippo 新人考试群。\n" +
                    "📋 新人指引：\n" +
                    "请优先查阅群公告，知悉群内相关要求\n" +
                    "详细阅读《Hippo 项目新人指南》，了解项目与考核流程\n" +
                    "若有问题，可@徐璐菲/@王民从获取协助\n";

            JSONObject gContent = new JSONObject();
            gContent.put("text", groupTxt);

            JSONObject gBody = new JSONObject();
            gBody.put("receive_id", chatId);
            gBody.put("msg_type", "text");
            gBody.put("content", gContent.toJSONString());

            postRequest(groupUrl, gBody.toJSONString(), token);
        }
    }

    private void sendMcpTrialGroupWelcome(String chatId, String userId, String token) {
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
        String welcomeContent =
            "<at user_id=\"" + userId + "\"></at>  欢迎加入 MCP 试标群！\n" +
            "快速上手指南：\n" +
            "先看群公告（群聊右上角点击进入/手机端右上角群应用点击“公告”） → 了解群规与注意事项\n" +
            "再读《MCP 培训文档》（https://meetchances.feishu.cn/wiki/XgJHwAGqkiAk6MkH0eXcpMX0nXg?from=from_copylink） → 掌握考核与操作流程\n" +
            "项目火热进行中！持续到清明节后，需求超 500+ 道题\n" +
            "每完成一道题，即可获得 300-500 元收益，多劳多得！\n" +
            "还有额外内推奖励哦！\n" +
            "有任何问题，随时 @群管理员 为你解答\n";

        JSONObject content = new JSONObject();
        content.put("text", welcomeContent);

        JSONObject body = new JSONObject();
        body.put("receive_id", chatId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        postRequest(url, body.toJSONString(), token);
    }

    private void postRequest(String url, String jsonBody, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForObject(url, new HttpEntity<>(jsonBody, headers), String.class);
    }

    private String getTenantAccessToken() {
        String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);
        JSONObject resp = restTemplate.postForObject(url, body, JSONObject.class);
        return resp.getString("tenant_access_token");
    }
}
