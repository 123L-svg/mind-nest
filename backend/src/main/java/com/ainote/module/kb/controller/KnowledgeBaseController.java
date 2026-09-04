package com.ainote.module.kb.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.kb.dto.KbDTO;
import com.ainote.module.kb.service.KnowledgeBaseService;
import com.ainote.module.kb.vo.KbVO;
import com.ainote.module.log.annotation.OperLog;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库接口
 */
@Tag(name = "知识库模块")
@RestController
@RequestMapping("/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService kbService;

    @Operation(summary = "我的知识库列表")
    @GetMapping("/list")
    public Result<List<KbVO>> list() {
        return Result.success(kbService.listMine(SecurityUtil.getUserId()));
    }

    @Operation(summary = "新建知识库")
    @OperLog(module = "知识库", action = "新建知识库")
    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody KbDTO dto) {
        return Result.success(kbService.add(SecurityUtil.getUserId(), dto));
    }

    @Operation(summary = "编辑知识库")
    @OperLog(module = "知识库", action = "编辑知识库")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody KbDTO dto) {
        kbService.update(SecurityUtil.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除知识库")
    @OperLog(module = "知识库", action = "删除知识库")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        kbService.delete(SecurityUtil.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "知识库详情")
    @GetMapping("/detail/{id}")
    public Result<KbVO> detail(@PathVariable("id") Long id) {
        return Result.success(kbService.detail(SecurityUtil.getUserId(), id));
    }
}