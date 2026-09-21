package com.ainote.module.publicview.controller;

import com.ainote.common.ratelimit.RateLimit;
import com.ainote.common.result.Result;
import com.ainote.module.kb.service.KnowledgeBaseService;
import com.ainote.module.kb.vo.KbVO;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.service.NoteService;
import com.ainote.module.note.vo.NoteVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 公开（分享）只读接口：公开知识库游客可无 token 浏览
 */
@Tag(name = "公开分享")
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final KnowledgeBaseService kbService;
    private final NoteService noteService;

    @Operation(summary = "公开知识库详情")
    @GetMapping("/kb/{id}")
    @RateLimit(key = "public", capacity = 10, refillPerSecond = 5)
    public Result<KbVO> kb(@PathVariable("id") Long id) {
        return Result.success(kbService.publicDetail(id));
    }

    @Operation(summary = "公开知识库的笔记列表")
    @GetMapping("/kb/{id}/notes")
    @RateLimit(key = "public", capacity = 10, refillPerSecond = 5)
    public Result<List<NoteVO>> notes(@PathVariable("id") Long id,
                                      @RequestParam(value = "keyword", required = false) String keyword) {
        kbService.checkPublic(id);
        return Result.success(noteService.publicList(id, keyword));
    }

    @Operation(summary = "公开笔记详情")
    @GetMapping("/note/{id}")
    @RateLimit(key = "public", capacity = 10, refillPerSecond = 5)
    public Result<NoteVO> note(@PathVariable("id") Long id) {
        Note note = noteService.checkPublicReadable(id);
        kbService.checkPublic(note.getKbId());
        return Result.success(noteService.publicDetail(id));
    }
}