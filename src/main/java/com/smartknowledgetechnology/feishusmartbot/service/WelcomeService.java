package com.smartknowledgetechnology.feishusmartbot.service;

import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WelcomeService {

    private static final String PRIVATE_STATUS_CHAT_CREATED = "已打开会话";
    private static final String PRIVATE_STATUS_MESSAGE_RECEIVED = "已发送私信";
    private static final long PRIVATE_GUIDE_DEDUP_WINDOW_MS = 24L * 60 * 60 * 1000;

    private final FeishuApiClient apiClient;
    private final FeishuChatService chatService;
    private final FeishuBitableService bitableService;
    private final FeishuMessageService messageService;
    private final ChatGroupConfig chatGroupConfig;
    private final Map<String, Long> privateGuideSentAt = new ConcurrentHashMap<>();

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
            sendProjectPrivateGuide(userId, chatId, PRIVATE_STATUS_MESSAGE_RECEIVED, true);
        } catch (Exception e) {
            System.err.println("处理私聊消息失败: " + e.getMessage());
        }
    }

    public void handleBotP2pChatCreated(String userId, String chatId) {
        try {
            sendProjectPrivateGuide(userId, chatId, PRIVATE_STATUS_CHAT_CREATED, false);
        } catch (Exception e) {
            System.err.println("处理机器人私聊会话创建事件失败: " + e.getMessage());
        }
    }

    private void sendProjectPrivateGuide(String userId, String chatId, String status, boolean forceSend) {
        String token = apiClient.getTenantAccessToken();
        List<String> projectTypes = bitableService.resolveProjectTypesByOpenId(userId, token);
        String projectKey = projectTypes.isEmpty() ? "default" : String.join(",", projectTypes);
        if (!forceSend && !shouldSendPrivateGuide(userId, projectKey)) {
            System.out.println(">>> [PrivateGuide] skip duplicate send within window, userId=" + userId
                    + ", projectType=" + projectKey);
            return;
        }

        if (!projectTypes.isEmpty()) {
            for (String projectType : projectTypes) {
                bitableService.trackPrivateChatByProject(userId, chatId, status, token, projectType);
            }
        } else {
            System.out.println(">>> [PrivateGuide] project unresolved, fallback to default guide, userId=" + userId);
        }

        String userName = chatService.getUserName(chatId, userId, token);
        messageService.sendPrivateGuideByProjects(userId, userName, token, projectTypes);
        markPrivateGuideSent(userId, projectKey);
    }

    private boolean shouldSendPrivateGuide(String userId, String projectType) {
        String key = userId + "|" + projectType;
        Long lastSentAt = privateGuideSentAt.get(key);
        long now = System.currentTimeMillis();
        return lastSentAt == null || now - lastSentAt > PRIVATE_GUIDE_DEDUP_WINDOW_MS;
    }

    private void markPrivateGuideSent(String userId, String projectType) {
        String key = userId + "|" + projectType;
        privateGuideSentAt.put(key, System.currentTimeMillis());
    }
}
