package com.ainote.module.note.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.log.annotation.OperLog;
import com.ainote.module.note.dto.NoteDTO;
import com.ainote.module.note.entity.NoteVersion;
import com.ainote.module.note.service.NoteService;
import com.ainote.module.note.vo.NoteVersionVO;
import com.ainote.module.note.vo.NoteVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 笔记接口
 */
@Tag(name = "笔记模块")
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @Operation(summary = "分页查询笔记（status=0）")
    @GetMapping("/list")
    public Result<Page<NoteVO>> list(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                     @RequestParam(value = "size", defaultValue = "10") Integer size,
                                     @RequestParam(value = "kbId", required = false) Long kbId,
                                     @RequestParam(value = "categoryId", required = false) Long categoryId,
                                     @RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(noteService.page(SecurityUtil.getUserId(), page, size, kbId, categoryId, 0, keyword));
    }

    @Operation(summary = "分页查询回收站（status=1）")
    @GetMapping("/recycle")
    public Result<Page<NoteVO>> recycle(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                        @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return Result.success(noteService.page(SecurityUtil.getUserId(), page, size, null, null, 1, null));
    }

    @Operation(summary = "新建笔记")
    @OperLog(module = "笔记", action = "新建笔记")
    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody NoteDTO dto) {
        return Result.success(noteService.add(SecurityUtil.getUserId(), dto));
    }

    @Operation(summary = "编辑笔记")
    @OperLog(module = "笔记", action = "编辑笔记")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody NoteDTO dto) {
        noteService.update(SecurityUtil.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "笔记详情")
    @GetMapping("/detail/{id}")
    public Result<NoteVO> detail(@PathVariable("id") Long id) {
        return Result.success(noteService.detail(SecurityUtil.getUserId(), id));
    }

    @Operation(summary = "移入回收站")
    @OperLog(module = "笔记", action = "移入回收站")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        noteService.delete(SecurityUtil.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "从回收站恢复")
    @OperLog(module = "笔记", action = "恢复笔记")
    @PutMapping("/restore/{id}")
    public Result<Void> restore(@PathVariable("id") Long id) {
        noteService.restore(SecurityUtil.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "永久删除（回收站内）")
    @DeleteMapping("/purge/{id}")
    public Result<Void> purge(@PathVariable("id") Long id) {
        noteService.permanentlyDelete(SecurityUtil.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "我的笔记版本列表")
    @GetMapping("/versions/{noteId}")
    public Result<List<NoteVersionVO>> versions(@PathVariable("noteId") Long noteId) {
        return Result.success(noteService.versions(SecurityUtil.getUserId(), noteId));
    }

    @Operation(summary = "版本详情（含内容）")
    @GetMapping("/version/{versionId}")
    public Result<NoteVersion> versionDetail(@PathVariable("versionId") Long versionId) {
        return Result.success(noteService.versionDetail(SecurityUtil.getUserId(), versionId));
    }

    @Operation(summary = "回滚到指定版本")
    @PostMapping("/rollback/{versionId}")
    public Result<Void> rollback(@PathVariable("versionId") Long versionId) {
        noteService.rollback(SecurityUtil.getUserId(), versionId);
        return Result.success();
    }
}