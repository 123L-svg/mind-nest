package com.ainote.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JwtUtil 令牌生成/解析测试
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-0123456789abcdefghijklmnop");
        ReflectionTestUtils.setField(jwtUtil, "expireMillis", 60_000L);
        jwtUtil.init();
    }

    @Test
    void generateAndParseToken() {
        Long uid = 1234567890L;
        String token = jwtUtil.generateToken(uid, "tester");
        assertNotNull(token);
        Long parsed = jwtUtil.getUserId(token);
        org.junit.jupiter.api.Assertions.assertEquals(uid, parsed);
    }

    @Test
    void invalidTokenReturnsNull() {
        assertNull(jwtUtil.getUserId("not-a-jwt"));
    }
}