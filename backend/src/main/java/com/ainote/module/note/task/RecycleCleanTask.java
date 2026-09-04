package com.ainote.module.note.task;

import com.ainote.module.note.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收站自动清理定时任务：每天凌晨 2 点执行，永久删除超期回收站笔记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecycleCleanTask {

    private final NoteService noteService;

    @Value("${note.recycle.keep-days:30}")
    private int keepDays;

    @Scheduled(cron = "0 0 2 * * ?")
    public void clean() {
        try {
            int n = noteService.cleanupRecycleBin(keepDays);
            if (n > 0) {
                log.info("回收站自动清理完成：永久删除 {} 篇超期笔记（> {} 天）", n, keepDays);
            }
        } catch (Exception e) {
            log.error("回收站自动清理失败：{}", e.getMessage(), e);
        }
    }
}