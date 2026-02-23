package com.project.aidoc.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SseControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testConnectWithoutLogin() throws Exception {
        // 测试未登录时访问connect接口
        MvcResult result = mockMvc.perform(
                get("/api/v1/sse/connect/test_session"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("未登录connect响应: " + responseContent);
        
        // 验证返回401
        assertTrue(responseContent.contains("\"code\":401"));
        assertTrue(responseContent.contains("\"msg\":\"用户未登录\""));
    }

    @Test
    public void testDisconnectWithoutLogin() throws Exception {
        // 测试未登录时访问disconnect接口
        MvcResult result = mockMvc.perform(
                post("/api/v1/sse/disconnect/test_session"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("未登录disconnect响应: " + responseContent);
        
        // 验证返回401
        assertTrue(responseContent.contains("\"code\":401"));
        assertTrue(responseContent.contains("\"msg\":\"用户未登录\""));
    }

    @Test
    public void testConnectionsWithoutLogin() throws Exception {
        // 测试未登录时访问connections接口
        MvcResult result = mockMvc.perform(
                get("/api/v1/sse/connections"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        System.out.println("未登录connections响应: " + responseContent);
        
        // 验证返回401
        assertTrue(responseContent.contains("\"code\":401"));
        assertTrue(responseContent.contains("\"msg\":\"用户未登录\""));
    }
}