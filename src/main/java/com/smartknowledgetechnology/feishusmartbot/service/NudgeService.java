package com.smartknowledgetechnology.feishusmartbot.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class NudgeService {

    private final FeishuApiClient apiClient;
    private final FeishuChatService chatService;
    private final FeishuBitableService bitableService;
    private final FeishuMessageService messageService;

    public NudgeService(FeishuApiClient apiClient,
                        FeishuChatService chatService,
                        FeishuBitableService bitableService,
                        FeishuMessageService messageService) {
        this.apiClient = apiClient;
        this.chatService = chatService;
        this.bitableService = bitableService;
        this.messageService = messageService;
    }

    public void executeNudge(String chatId, String tableId, String matchField, String customMessage) {
        executeNudge(chatId, null, tableId, matchField, customMessage);
    }

    public void executeNudge(String chatId, String appToken, String tableId, String matchField, String customMessage) {
        try {
            String token = apiClient.getTenantAccessToken();

            Map<String, String> allMembers = chatService.getAllChatMembers(chatId, token);
            System.out.println(">>> [催促] 群成员总数: " + allMembers.size());

            Set<String> submittedIds = appToken != null
                    ? bitableService.getSubmittedIds(appToken, tableId, matchField, token)
                    : bitableService.getSubmittedIds(tableId, matchField, token);
            System.out.println(">>> [催促] 已提交人数: " + submittedIds.size());

            Map<String, String> unsubmitted = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : allMembers.entrySet()) {
                if (!submittedIds.contains(entry.getKey())) {
                    unsubmitted.put(entry.getKey(), entry.getValue());
                }
            }
            System.out.println(">>> [催促] 未提交人数: " + unsubmitted.size());

            if (unsubmitted.isEmpty()) {
                System.out.println(">>> [催促] 所有人都已提交，无需催促");
                return;
            }

            String nudgeText = customMessage != null ? customMessage
                    : "您好，提醒您尽快完成数据提交，如有疑问请联系群管理员。";

            for (Map.Entry<String, String> entry : unsubmitted.entrySet()) {
                String userId = entry.getKey();
                String userName = entry.getValue();
                try {
                    boolean sent = messageService.sendEphemeralCard(chatId, userId, userName, nudgeText, token);
                    if (!sent) {
                        messageService.sendPrivateTextMessageChecked(userId,
                                userName + " 你好，\n" + nudgeText, token, "私聊催促 " + userName);
                    }
                } catch (Exception e) {
                    System.err.println(">>> [催促] 发送失败 userId=" + userId + ": " + e.getMessage());
                }
            }
            System.out.println(">>> [催促] 催促完成");
        } catch (Exception e) {
            System.err.println(">>> [催促] 执行异常: " + e.getMessage());
        }
    }
}
