package com.smartknowledgetechnology.feishusmartbot.service;

import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.springframework.stereotype.Service;

@Service
public class WelcomeService {

    private final FeishuApiClient apiClient;
    private final FeishuChatService chatService;
    private final FeishuBitableService bitableService;
    private final FeishuMessageService messageService;
    private final ChatGroupConfig chatGroupConfig;

    public WelcomeService(FeishuApiClient apiClient,
                          FeishuChatService chatService,
                          FeishuBitableService bitableService,
                          FeishuMessageService messageService,
                          ChatGroupConfig chatGroupConfig) {
        this.apiClient = apiClient;
        this.chatService = chatService;
        this.bitableService = bitableService;
        this.messageService = messageService;
        this.chatGroupConfig = chatGroupConfig;
    }

    public void handleJoinEvent(String chatId, String userId) {
        try {
            System.out.println(">>> handleJoinLogic: chatId=" + chatId + ", userId=" + userId);
            String token = apiClient.getTenantAccessToken();
            String userName = chatService.getUserName(chatId, userId, token);

            String chatType = chatGroupConfig.getChatType(chatId);
            if (chatType == null) {
                System.out.println(">>> 未匹配到群配置，chatId=" + chatId);
                return;
            }

            // 测试群不写多维表格
            String tableId = chatGroupConfig.getTableIdForChat(chatId);
            if (tableId != null) {
                bitableService.writeToBitable(userName, userId, token, tableId);
            }

            switch (chatType) {
                case "test" -> messageService.sendEphemeralWelcome(chatId, userId, userName, token);
                case "trial" -> messageService.sendTrialGroupWelcome(chatId, userId, token);
                case "newbie" -> messageService.sendNewbieNotice(chatId, userId, userName,
                        chatGroupConfig.isPrivateOnlyChat(chatId), token);
                case "mcp_trial" -> messageService.sendMcpTrialGroupWelcome(chatId, userId, token);
                case "coding_agent_trial", "coding_agent_formal" ->
                        messageService.sendCodingAgentGroupWelcome(chatId, userId, token);
                case "hippo3_exam" -> messageService.sendHippo3ExamGroupWelcome(chatId, userId, token);
                case "hippo3_formal" -> messageService.sendHippo3FormalGroupWelcome(chatId, userId, token);
                case "claudecode_formal" -> messageService.sendClaudeCodeFormalGroupWelcome(chatId, userId, token);
            }
        } catch (Exception e) {
            System.err.println("处理入群事件失败: " + e.getMessage());
        }
    }

    public void handlePrivateMessage(String userId, String chatId) {
        try {
            String token = apiClient.getTenantAccessToken();
            String userName = chatService.getUserName(chatId, userId, token);
            messageService.sendPrivateGuide(userId, userName, token);
        } catch (Exception e) {
            System.err.println("处理私聊消息失败: " + e.getMessage());
        }
    }
}
