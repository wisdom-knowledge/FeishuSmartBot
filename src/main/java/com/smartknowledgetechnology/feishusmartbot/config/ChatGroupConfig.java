package com.smartknowledgetechnology.feishusmartbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ChatGroupConfig {

    @Value("${feishu.bitable.app-token}")
    private String bitableAppToken;

    @Value("${feishu.bitable.table-id.trial}")
    private String bitableTableIdTrial;

    @Value("${feishu.bitable.table-id.newbie}")
    private String bitableTableIdNewbie;

    @Value("${feishu.bitable.table-id.mcp.trial}")
    private String bitableTableIdMcpTrial;

    @Value("${feishu.bitable.table-id.coding_agent.trial}")
    private String bitableTableIdCodingAgentTrial;

    @Value("${feishu.bitable.table-id.coding_agent.formal}")
    private String bitableTableIdCodingAgentFormal;

    @Value("${feishu.bitable.table-id.hippo3.exam}")
    private String bitableTableIdHippo3Exam;

    @Value("${feishu.bitable.table-id.hippo3.formal}")
    private String bitableTableIdHippo3Formal;

    @Value("${feishu.bitable.table-id.claudecode.formal}")
    private String bitableTableIdClaudeCodeFormal;

    @Value("${feishu.chat.target-id.trial}")
    private String targetChatIdTrial;

    @Value("${feishu.chat.target-id.newbie}")
    private String targetChatIdNewbie;

    @Value("${feishu.chat.target-id.mcp.trial}")
    private String targetChatIdMcpTrial;

    @Value("${feishu.chat.target-id.coding_agent.trial}")
    private String targetChatIdCodingAgentTrial;

    @Value("${feishu.chat.target-id.coding_agent.formal}")
    private String targetChatIdCodingAgentFormal;

    @Value("${feishu.chat.target-id.hippo3.exam}")
    private String targetChatIdHippo3Exam;

    @Value("${feishu.chat.target-id.hippo3.formal}")
    private String targetChatIdHippo3Formal;

    @Value("${feishu.chat.target-id.claudecode.formal}")
    private String targetChatIdClaudeCodeFormal;

    @Value("${feishu.chat.target-id.test:}")
    private String targetChatIdTest;

    @Value("${feishu.chat.private-only-ids:}")
    private String privateOnlyChatIds;

    public String getBitableAppToken() {
        return bitableAppToken;
    }

    /**
     * 根据 chatId 查找对应的多维表格 tableId，未匹配返回 null
     */
    public String getTableIdForChat(String chatId) {
        if (targetChatIdTrial.equals(chatId)) return bitableTableIdTrial;
        if (targetChatIdNewbie.equals(chatId)) return bitableTableIdNewbie;
        if (targetChatIdMcpTrial.equals(chatId)) return bitableTableIdMcpTrial;
        if (targetChatIdCodingAgentTrial.equals(chatId)) return bitableTableIdCodingAgentTrial;
        if (targetChatIdCodingAgentFormal.equals(chatId)) return bitableTableIdCodingAgentFormal;
        if (targetChatIdHippo3Exam.equals(chatId)) return bitableTableIdHippo3Exam;
        if (targetChatIdHippo3Formal.equals(chatId)) return bitableTableIdHippo3Formal;
        if (targetChatIdClaudeCodeFormal.equals(chatId)) return bitableTableIdClaudeCodeFormal;
        return null;
    }

    /**
     * 根据 chatId 查找群组类型标识，未匹配返回 null
     */
    public String getChatType(String chatId) {
        if (targetChatIdTrial.equals(chatId)) return "trial";
        if (targetChatIdNewbie.equals(chatId)) return "newbie";
        if (targetChatIdMcpTrial.equals(chatId)) return "mcp_trial";
        if (targetChatIdCodingAgentTrial.equals(chatId)) return "coding_agent_trial";
        if (targetChatIdCodingAgentFormal.equals(chatId)) return "coding_agent_formal";
        if (targetChatIdHippo3Exam.equals(chatId)) return "hippo3_exam";
        if (targetChatIdHippo3Formal.equals(chatId)) return "hippo3_formal";
        if (targetChatIdClaudeCodeFormal.equals(chatId)) return "claudecode_formal";
        if (targetChatIdTest != null && targetChatIdTest.equals(chatId)) return "test";
        return null;
    }

    public boolean isPrivateOnlyChat(String chatId) {
        if (chatId == null || chatId.isEmpty() || privateOnlyChatIds == null || privateOnlyChatIds.trim().isEmpty()) {
            return false;
        }
        for (String id : privateOnlyChatIds.split(",")) {
            if (chatId.equals(id.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取所有已配置的 chatId -> chatType 映射
     */
    public Map<String, String> getAllChatMappings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(targetChatIdTrial, "trial");
        map.put(targetChatIdNewbie, "newbie");
        map.put(targetChatIdMcpTrial, "mcp_trial");
        map.put(targetChatIdCodingAgentTrial, "coding_agent_trial");
        map.put(targetChatIdCodingAgentFormal, "coding_agent_formal");
        map.put(targetChatIdHippo3Exam, "hippo3_exam");
        map.put(targetChatIdHippo3Formal, "hippo3_formal");
        map.put(targetChatIdClaudeCodeFormal, "claudecode_formal");
        return map;
    }
}
