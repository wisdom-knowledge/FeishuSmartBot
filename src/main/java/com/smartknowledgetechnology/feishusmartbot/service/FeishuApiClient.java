package com.smartknowledgetechnology.feishusmartbot.service;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class FeishuApiClient {

    @Value("${feishu.app.id}")
    private String appId;

    @Value("${feishu.app.secret}")
    private String appSecret;

    private final RestTemplate restTemplate = new RestTemplate();

    public String getTenantAccessToken() {
        String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);
        JSONObject resp = restTemplate.postForObject(url, body, JSONObject.class);
        return resp.getString("tenant_access_token");
    }

    public void postRequest(String url, String jsonBody, String token) {
        HttpHeaders headers = buildAuthHeaders(token);
        restTemplate.postForObject(url, new HttpEntity<>(jsonBody, headers), String.class);
    }

    public String postRequestWithResponse(String url, String jsonBody, String token) {
        HttpHeaders headers = buildAuthHeaders(token);
        return restTemplate.postForObject(url, new HttpEntity<>(jsonBody, headers), String.class);
    }

    public boolean postRequestAndCheckSuccess(String url, String jsonBody, String token, String scene) {
        try {
            String respText = postRequestWithResponse(url, jsonBody, token);
            JSONObject resp = JSONObject.parseObject(respText);
            Integer code = resp.getInteger("code");
            if (code != null && code == 0) {
                return true;
            }
            System.err.println(">>> " + scene + "失败: response=" + respText);
            return false;
        } catch (Exception e) {
            System.err.println(">>> " + scene + "异常: " + e.getMessage());
            return false;
        }
    }

    public ResponseEntity<JSONObject> getRequest(String url, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), JSONObject.class);
    }

    private HttpHeaders buildAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
