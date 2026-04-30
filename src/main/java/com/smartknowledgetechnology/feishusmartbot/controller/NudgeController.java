package com.smartknowledgetechnology.feishusmartbot.controller;

import com.alibaba.fastjson2.JSONObject;
import com.smartknowledgetechnology.feishusmartbot.service.FeishuApiClient;
import com.smartknowledgetechnology.feishusmartbot.service.FeishuChatService;
import com.smartknowledgetechnology.feishusmartbot.service.FeishuMessageService;
import com.smartknowledgetechnology.feishusmartbot.service.NudgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
public class NudgeController {

    private final NudgeService nudgeService;
    private final FeishuApiClient apiClient;
    private final FeishuChatService chatService;
    private final FeishuMessageService messageService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    public NudgeController(NudgeService nudgeService,
                           FeishuApiClient apiClient,
                           FeishuChatService chatService,
                           FeishuMessageService messageService) {
        this.nudgeService = nudgeService;
        this.apiClient = apiClient;
        this.chatService = chatService;
        this.messageService = messageService;
    }

    /**
     * 运营调用此接口催促群内未提交数据的人。
     *
     * 请求体参数：
     *   chatId   — 目标群的 chat_id（必填）
     *   tableId  — 多维表格中已提交人员所在的 table_id（必填）
     *   appToken — 多维表格的应用 Token（可选，默认使用配置文件中的值）
     *   field    — 多维表格中存放提交者标识的字段名（可选，默认 "OpenID"）
     *   message  — 催促文案（可选，有默认值）
     */
    @PostMapping("/nudge_unsubmitted")
    public Map<String, Object> nudgeUnsubmitted(@RequestBody JSONObject params) {
        String chatId = params.getString("chatId");
        String tableId = params.getString("tableId");
        String appToken = params.containsKey("appToken") ? params.getString("appToken") : null;
        String field = params.containsKey("field") ? params.getString("field") : "OpenID";
        String message = params.containsKey("message") ? params.getString("message") : null;

        if (chatId == null || chatId.isEmpty() || tableId == null || tableId.isEmpty()) {
            return Map.of("code", 400, "msg", "chatId 和 tableId 为必填参数");
        }

        executorService.submit(() -> nudgeService.executeNudge(chatId, appToken, tableId, field, message));
        return Map.of("code", 0, "msg", "催促任务已提交，正在异步执行");
    }

    /**
     * 测试接口：给群内所有成员发送一条仅本人可见的临时卡片。
     * 参数：chatId（必填），message（可选）
     */
    @PostMapping("/test_ephemeral")
    public Map<String, Object> testEphemeral(@RequestBody JSONObject params) {
        String chatId = params.getString("chatId");
        String message = params.containsKey("message") ? params.getString("message") : null;

        if (chatId == null || chatId.isEmpty()) {
            return Map.of("code", 400, "msg", "chatId 为必填参数");
        }

        executorService.submit(() -> {
            try {
                String token = apiClient.getTenantAccessToken();
                Map<String, String> members = chatService.getAllChatMembers(chatId, token);
                System.out.println(">>> [测试] 群成员数: " + members.size());

                String text = message != null ? message
                        : "这是一条仅你可见的测试消息，群内其他人看不到。\n\n如果你能看到这张卡片，说明临时消息卡片功能正常工作！";

                for (Map.Entry<String, String> entry : members.entrySet()) {
                    String userId = entry.getKey();
                    String userName = entry.getValue();
                    boolean ok = messageService.sendEphemeralWelcome(chatId, userId, userName, token);
                    System.out.println(">>> [测试] 发送给 " + userName + "(" + userId + "): " + (ok ? "成功" : "失败"));
                }
                System.out.println(">>> [测试] 全部发送完成");
            } catch (Exception e) {
                System.err.println(">>> [测试] 异常: " + e.getMessage());
            }
        });

        return Map.of("code", 0, "msg", "测试任务已提交，正在异步发送临时卡片给群内所有成员");
    }
}
