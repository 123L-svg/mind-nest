package com.ainote;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MindNest（智巢）· AI 智能笔记与知识库系统 - 启动类
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.ainote.module.**.mapper")
public class AiNoteApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiNoteApplication.class, args);
    }
}