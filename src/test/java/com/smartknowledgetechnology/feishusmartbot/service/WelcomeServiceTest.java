package com.smartknowledgetechnology.feishusmartbot.service;

import com.smartknowledgetechnology.feishusmartbot.config.ChatGroupConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WelcomeServiceTest {

    @Test
    void handleBotP2pChatCreatedSendsGuideAfterSessionCreated() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuChatService chatService = mock(FeishuChatService.class);
        FeishuBitableService bitableService = mock(FeishuBitableService.class);
        FeishuMessageService messageService = mock(FeishuMessageService.class);
        ChatGroupConfig chatGroupConfig = mock(ChatGroupConfig.class);
        WelcomeService service = new WelcomeService(apiClient, chatService, bitableService, messageService, chatGroupConfig);

        when(apiClient.getTenantAccessToken()).thenReturn("tenant-token");
        when(bitableService.resolveProjectTypesByOpenId("ou_test_user", "tenant-token"))
                .thenReturn(List.of("claudecode_formal"));
        when(chatService.getUserName("oc_p2p_chat", "ou_test_user", "tenant-token"))
                .thenReturn("测试用户");

        service.handleBotP2pChatCreated("ou_test_user", "oc_p2p_chat");

        verify(bitableService).trackPrivateChatByProject(
                "ou_test_user", "oc_p2p_chat", "已打开会话", "tenant-token", "claudecode_formal");
        verify(messageService).sendPrivateGuideByProjects(
                "ou_test_user", "测试用户", "tenant-token", List.of("claudecode_formal"));
    }

    @Test
    void handlePrivateMessageFallsBackToDefaultGuideWhenProjectUnresolved() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuChatService chatService = mock(FeishuChatService.class);
        FeishuBitableService bitableService = mock(FeishuBitableService.class);
        FeishuMessageService messageService = mock(FeishuMessageService.class);
        ChatGroupConfig chatGroupConfig = mock(ChatGroupConfig.class);
        WelcomeService service = new WelcomeService(apiClient, chatService, bitableService, messageService, chatGroupConfig);

        when(apiClient.getTenantAccessToken()).thenReturn("tenant-token");
        when(bitableService.resolveProjectTypesByOpenId("ou_test_user", "tenant-token"))
                .thenReturn(List.of());
        when(chatService.getUserName("oc_p2p_chat", "ou_test_user", "tenant-token"))
                .thenReturn("测试用户");

        service.handlePrivateMessage("ou_test_user", "oc_p2p_chat");

        verify(bitableService, never()).trackPrivateChatByProject(
                anyString(), anyString(), anyString(), anyString(), anyString());
        verify(messageService).sendPrivateGuideByProjects(
                "ou_test_user", "测试用户", "tenant-token", List.of());
    }

    @Test
    void handlePrivateMessageAlwaysRepliesForEachIncomingMessage() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuChatService chatService = mock(FeishuChatService.class);
        FeishuBitableService bitableService = mock(FeishuBitableService.class);
        FeishuMessageService messageService = mock(FeishuMessageService.class);
        ChatGroupConfig chatGroupConfig = mock(ChatGroupConfig.class);
        WelcomeService service = new WelcomeService(apiClient, chatService, bitableService, messageService, chatGroupConfig);

        when(apiClient.getTenantAccessToken()).thenReturn("tenant-token");
        when(bitableService.resolveProjectTypesByOpenId("ou_test_user", "tenant-token"))
                .thenReturn(List.of("claudecode_formal"));
        when(chatService.getUserName("oc_p2p_chat", "ou_test_user", "tenant-token"))
                .thenReturn("测试用户");

        service.handlePrivateMessage("ou_test_user", "oc_p2p_chat");
        service.handlePrivateMessage("ou_test_user", "oc_p2p_chat");

        verify(messageService, times(2)).sendPrivateGuideByProjects(
                "ou_test_user", "测试用户", "tenant-token", List.of("claudecode_formal"));
        verify(bitableService, times(2)).trackPrivateChatByProject(
                eq("ou_test_user"), eq("oc_p2p_chat"), eq("已发送私信"), eq("tenant-token"), eq("claudecode_formal"));
    }

    @Test
    void handlePrivateMessageTracksAndSendsCombinedGuideForMultipleProjects() {
        FeishuApiClient apiClient = mock(FeishuApiClient.class);
        FeishuChatService chatService = mock(FeishuChatService.class);
        FeishuBitableService bitableService = mock(FeishuBitableService.class);
        FeishuMessageService messageService = mock(FeishuMessageService.class);
        ChatGroupConfig chatGroupConfig = mock(ChatGroupConfig.class);
        WelcomeService service = new WelcomeService(apiClient, chatService, bitableService, messageService, chatGroupConfig);

        when(apiClient.getTenantAccessToken()).thenReturn("tenant-token");
        when(bitableService.resolveProjectTypesByOpenId("ou_test_user", "tenant-token"))
                .thenReturn(List.of("claudecode_formal", "newbie"));
        when(chatService.getUserName("oc_p2p_chat", "ou_test_user", "tenant-token"))
                .thenReturn("测试用户");

        service.handlePrivateMessage("ou_test_user", "oc_p2p_chat");

        verify(bitableService).trackPrivateChatByProject(
                "ou_test_user", "oc_p2p_chat", "已发送私信", "tenant-token", "claudecode_formal");
        verify(bitableService).trackPrivateChatByProject(
                "ou_test_user", "oc_p2p_chat", "已发送私信", "tenant-token", "newbie");
        verify(messageService).sendPrivateGuideByProjects(
                "ou_test_user", "测试用户", "tenant-token", List.of("claudecode_formal", "newbie"));
    }
}
