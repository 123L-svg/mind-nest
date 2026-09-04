package com.ainote.module.category.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.category.dto.CategoryDTO;
import com.ainote.module.category.service.CategoryService;
import com.ainote.module.category.vo.CategoryVO;
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
 * 分类接口
 */
@Tag(name = "分类模块")
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "知识库下的分类列表")
    @GetMapping("/list")
    public Result<List<CategoryVO>> list(@RequestParam("kbId") Long kbId) {
        return Result.success(categoryService.list(SecurityUtil.getUserId(), kbId));
    }

    @Operation(summary = "新建分类")
    @PostMapping("/add")
    public Result<Long> add(@Valid @RequestBody CategoryDTO dto) {
        return Result.success(categoryService.add(SecurityUtil.getUserId(), dto));
    }

    @Operation(summary = "编辑分类")
    @PutMapping("/update")
    public Result<Void> update(@Valid @RequestBody CategoryDTO dto) {
        categoryService.update(SecurityUtil.getUserId(), dto);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        categoryService.delete(SecurityUtil.getUserId(), id);
        return Result.success();
    }
}