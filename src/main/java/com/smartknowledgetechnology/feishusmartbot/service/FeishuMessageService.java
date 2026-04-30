package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

@Service
public class FeishuMessageService {

    private final FeishuApiClient apiClient;

    public FeishuMessageService(FeishuApiClient apiClient) {
        this.apiClient = apiClient;
    }



    public void sendGroupTextMessage(String chatId, String text, String token) {
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
        JSONObject content = new JSONObject();
        content.put("text", text);

        JSONObject body = new JSONObject();
        body.put("receive_id", chatId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        apiClient.postRequest(url, body.toJSONString(), token);
    }



    public void sendPrivateTextMessage(String userId, String text, String token) {
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id";
        JSONObject content = new JSONObject();
        content.put("text", text);

        JSONObject body = new JSONObject();
        body.put("receive_id", userId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        apiClient.postRequest(url, body.toJSONString(), token);
    }

    public boolean sendPrivateTextMessageChecked(String userId, String text, String token, String scene) {
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=open_id";
        JSONObject content = new JSONObject();
        content.put("text", text);

        JSONObject body = new JSONObject();
        body.put("receive_id", userId);
        body.put("msg_type", "text");
        body.put("content", content.toJSONString());

        return apiClient.postRequestAndCheckSuccess(url, body.toJSONString(), token, scene);
    }



    public boolean sendEphemeralCard(String chatId, String userId, String userName,
                                     String nudgeText, String token) {
        String url = "https://open.feishu.cn/open-apis/ephemeral/v1/send";

        JSONObject card = new JSONObject();
        card.put("schema", "2.0");

        JSONObject header = new JSONObject();
        JSONObject title = new JSONObject();
        title.put("content", "提交提醒");
        title.put("tag", "plain_text");
        header.put("title", title);
        header.put("template", "orange");
        card.put("header", header);

        JSONObject bodyObj = new JSONObject();
        JSONArray elements = new JSONArray();
        JSONObject markdown = new JSONObject();
        markdown.put("tag", "markdown");
        markdown.put("content", "**" + userName + "** 你好，\n\n" + nudgeText);
        elements.add(markdown);
        bodyObj.put("elements", elements);
        card.put("body", bodyObj);

        JSONObject body = new JSONObject();
        body.put("chat_id", chatId);
        body.put("open_id", userId);
        body.put("msg_type", "interactive");
        body.put("card", card);

        return apiClient.postRequestAndCheckSuccess(url, body.toJSONString(), token, "临时卡片催促 " + userName);
    }


    /**
     * 发送仅本人可见的欢迎临时卡片（用于测试群等场景）
     */
    public boolean sendEphemeralWelcome(String chatId, String userId, String userName, String token) {
        String url = "https://open.feishu.cn/open-apis/ephemeral/v1/send";

        JSONObject card = new JSONObject();
        card.put("schema", "2.0");

        JSONObject header = new JSONObject();
        JSONObject title = new JSONObject();
        title.put("content", "欢迎入群");
        title.put("tag", "plain_text");
        header.put("title", title);
        header.put("template", "blue");
        card.put("header", header);

        JSONObject bodyObj = new JSONObject();
        JSONArray elements = new JSONArray();

        JSONObject markdown = new JSONObject();
        markdown.put("tag", "markdown");
        markdown.put("content",
                "**" + userName + "** 你好，欢迎加入！\n\n" +
                "这是一条 **仅你可见** 的消息卡片，群内其他人看不到哦。\n\n" +
                "---\n" +
                "**入群须知：**\n" +
                "1. 请先查看群公告，了解群规\n" +
                "2. 有任何问题，欢迎 @群管理员 咨询\n" +
                "3. 祝你在这里一切顺利！");
        elements.add(markdown);
        bodyObj.put("elements", elements);
        card.put("body", bodyObj);

        JSONObject body = new JSONObject();
        body.put("chat_id", chatId);
        body.put("open_id", userId);
        body.put("msg_type", "interactive");
        body.put("card", card);

        return apiClient.postRequestAndCheckSuccess(url, body.toJSONString(), token, "临时卡片欢迎 " + userName);
    }

    public void sendTrialGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at> 欢迎通过面试，进入试标阶段，请务必阅读以下内容：\n" +
                "1.进入试标群并查看群公告\n" +
                "2.仔细阅读《Hippo项目指南》：\n" +
                "https://meetchances.feishu.cn/docx/QJdZd5MjxoD2YJxOiZcc6tAFnBe";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendNewbieNotice(String chatId, String userId, String userName,
                                  boolean isPrivateOnly, String token) {
        String privateContent = "你好 " + userName + "，欢迎加入新人群！\n\n" +
                "🎉 新人须知：\n" +
                "1. 请收看群公告\n" +
                "2. 查阅群公告中的《Hippo项目新人指南》\n" +
                "3. 有问题可@群管理员咨询";

        boolean privateOk = sendPrivateTextMessageChecked(userId, privateContent, token, "新人入群私聊欢迎");
        if (privateOk) return;

        if (isPrivateOnly) {
            System.out.println(">>> 当前群配置为仅私聊模式，私聊失败后不再群内发送欢迎消息。chatId=" + chatId + ", userId=" + userId);
            return;
        }

        String groupTxt = "<at user_id=\"" + userId + "\"></at> 你好，欢迎加入 Hippo 新人考试群。\n" +
                "📋 新人指引：\n" +
                "请优先查阅群公告，知悉群内相关要求\n" +
                "详细阅读《Hippo 项目新人指南》，了解项目与考核流程\n" +
                "若你未收到机器人私信，请先点击群内机器人头像并开启对话，随后再私聊机器人任意内容即可收到自动指引。";
        sendGroupTextMessage(chatId, groupTxt, token);
    }

    public void sendMcpTrialGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at>  欢迎加入 MCP 试标群！\n" +
                "快速上手指南：\n" +
                "先看群公告（群聊右上角点击进入/手机端右上角群应用点击“公告”） → 了解群规与注意事项\n" +
                "再读《MCP 培训文档》（https://meetchances.feishu.cn/wiki/XgJHwAGqkiAk6MkH0eXcpMX0nXg?from=from_copylink） → 掌握考核与操作流程\n" +
                "项目即将截止！持续到4月3号12点30分，请快快提交哦！\n" +
                "每完成一道题，即可获得 300-500 元收益，多劳多得！\n" +
                "还有额外内推奖励哦！\n" +
                "有任何问题，随时 @群管理员 为你解答";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendCodingAgentGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at> 欢迎加入 solo coder 试标群！快速上手指南：\n" +
                "做题前准备\n" +
                "1.如果您做过笔试入项小测且通过：直接去领题即可，以下是领题做题流程\n" +
                "https://meetchances.feishu.cn/docx/SAQXdMjztoYIxIxoaYXcEYshnGc?from=from_copylink\n" +
                "2如果您只参加过面试，没有参加过入项小测：需要先填写千识ID【填写入口 https://meetchances.feishu.cn/share/base/form/shrcna43spqsXaUYoRpQa1HvbBf 】，负责人会拉您进项目，半小时左右会在一面千识平台看到项目，点击申请并参加入项小测，通过后参照流程1\n" +
                "做题指南\n" +
                "做题前请仔细阅读培训文档\n" +
                "https://meetchances.feishu.cn/wiki/MosDw95SFiP4y0kMU0YclqelnHc?from=from_copylink\n" +
                "关于报酬\n" +
                "如果您使用trae多开，可以节省很多模型等待时间，每日收入预期：800-1200元\n" +
                "并且我们还设有满单奖，报酬多多，多劳多得～\n" +
                "本次项目数据需求量非常大，不限上限，只要质量高可以不断做题～\n" +
                "关于内推\n" +
                "本项目开启内推奖励，如果您内推的专家提交并通过轮次>10轮（约为3-4条数据），您可以获得200元内推奖励\n\n" +
                "有任何问题，随时 @群管理员 为你解答";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendHippo3ExamGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at>【专家】恭喜您通过面试，进入Hippo3.0考试阶段，请务必阅读以下内容：\n" +
                "1.查看群公告\n" +
                "群公告查看地址：\n" +
                "  • PC端：方法一：点击群名称下方灰色小字\"背景 我们正在征集...\" 进入群公告；方法二：点击最右侧一栏中，最上方的小黑板图标，进入群公告\n" +
                "  • 手机端：点击右上方三个点（群名称旁边的三个点），在群应用中找到群公告\n" +
                "2.仔细阅读《Hippo3.0 考试指南》：\n" +
                "https://meetchances.feishu.cn/docx/JTZwdpOCLozKRdxLPvmcWHphnqb?from=from_copylink\n" +
                "3. 考试通过后，您会收到包含正式群链接的飞书消息。点击该链接即可入群";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendHippo3FormalGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at>【专家】恭喜您进入正式作业阶段，请务必阅读以下内容：\n" +
                "1.查看群公告\n" +
                "群公告查看地址：\n" +
                "  • PC端：方法一：点击群名称下方灰色小字\"背景 我们正在征集...\" 进入群公告；方法二：点击最右侧一栏中，最上方的小黑板图标，进入群公告\n" +
                "  • 手机端：点击右上方三个点（群名称旁边的三个点），在群应用中找到群公告\n" +
                "2.仔细阅读《Hippo3.0 项目指南》：\n" +
                "https://meetchances.feishu.cn/docx/LhcFdwYIOow5sexdOvNctv84nEd?from=from_copylink";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendClaudeCodeFormalGroupWelcome(String chatId, String userId, String token) {
        String text = "<at user_id=\"" + userId + "\"></at>恭喜您进入正式任务阶段，请务必阅读以下内容：\n\n" +
                "加入我们的意义：\n" +
                "参与奖励：审核通过后，可获得 100 元现金奖励\n" +
                "专家认证：通过认证即成为一面千识平台认证 Claude Code 专家，优秀专家有机会登上官网展示\n" +
                "优先通道：认证后优先获得平台后续高价值项目邀约（单任务 200–2000 元）\n\n" +
                "为了尽快确认您的能力并纳入系统，请在进群后一周内完成任务提交，逾期将会被移除项目群。\n\n" +
                "如何提交：\n" +
                "打开https://talent.meetchances.com/home \n" +
                "登录 → 主页 → 我的项目\n" +
                "找到「Claude Code 认证专家」项目 → 点击进入项目\n" +
                "按页面提示填写并提交\n\n" +
                "任务提交指南：https://meetchances.feishu.cn/docx/NRtEdEmsqoIRuJxCpqZcwKYKnfd?from=from_copylink \n" +
                "Trace要求：https://meetchances.feishu.cn/wiki/IqYPwVYz6iBsJSkAdphcQLRanHf?from=from_copylink \n\n" +
                "项目流程：\n" +
                "进入项目群 → 提交任务 → 任务审核 → 提交返修 → 审核通过 → 解锁奖励及认证 → 等待后续高价值项目匹配";
        sendGroupTextMessage(chatId, text, token);
    }

    public void sendPrivateGuide(String userId, String userName, String token) {
        String text = "你好 " + userName + "，欢迎加入新人群！\n\n" +
                "🎉 新人须知：\n" +
                "1. 请收看群公告\n" +
                "2. 查阅群公告中的《Hippo项目新人指南》\n" +
                "3. 根据指南参加考试，通过进入试标\n" +
                "4. 有问题可@管理员";
        sendPrivateTextMessage(userId, text, token);
    }
}

