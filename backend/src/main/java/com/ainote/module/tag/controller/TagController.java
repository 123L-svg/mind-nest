package com.ainote.module.tag.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.tag.dto.TagDTO;
import com.ainote.module.tag.service.TagService;
import com.ainote.module.tag.vo.TagVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口
 */
@Tag(name = "标签模块")
@RestController
@RequestMapping("/tag")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    @Operation(summary = "我的标签列表")
    @GetMapping("/list")
    public Result<List<TagVO>> list() {
        return Result.success(tagService.list(SecurityUtil.getUserId()));
    }

    @Operation(summary = "新建标签")
    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody TagDTO dto) {
        return Result.success(tagService.add(SecurityUtil.getUserId(), dto));
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        tagService.delete(SecurityUtil.getUserId(), id);
        return Result.success();
    }
}