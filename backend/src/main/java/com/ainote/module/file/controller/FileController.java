package com.ainote.module.file.controller;

import com.ainote.common.result.Result;
import com.ainote.common.util.SecurityUtil;
import com.ainote.module.file.service.FileStorageService;
import com.ainote.module.file.vo.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件接口
 */
@Tag(name = "文件模块")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileStorageService fileStorageService;

    @Operation(summary = "上传文件")
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileStorageService.upload(SecurityUtil.getUserId(), file));
    }

    @Operation(summary = "绑定文件到笔记")
    @PutMapping("/bind")
    public Result<Void> bind(@RequestParam("fileId") Long fileId,
                             @RequestParam("noteId") Long noteId) {
        fileStorageService.bindToNote(SecurityUtil.getUserId(), fileId, noteId);
        return Result.success();
    }

    @Operation(summary = "笔记下的文件列表")
    @GetMapping("/list")
    public Result<List<FileVO>> list(@RequestParam(value = "noteId", required = false) Long noteId) {
        // 简单起见按用户返回全部（可按 noteId 过滤，此处返回该用户文件）
        List<FileVO> vos = fileStorageService.listByNote(SecurityUtil.getUserId(), noteId);
        return Result.success(vos);
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        fileStorageService.delete(SecurityUtil.getUserId(), id);
        return Result.success();
    }

    @Operation(summary = "下载文件（需登录且仅本人文件）")
    @GetMapping("/download/{id}")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) throws IOException {
        FileStorageService.FileDownload d = fileStorageService.download(SecurityUtil.getUserId(), id);
        // 内联预览图片，其余作为附件下载
        String disposition = (d.getContentType() != null && d.getContentType().startsWith("image/"))
                ? "inline" : "attachment";
        String fileName = d.getOriginalName() == null ? "file" : d.getOriginalName();
        response.setContentType(d.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE : d.getContentType());
        response.setHeader("Content-Disposition", disposition + "; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try (java.io.InputStream in = d.getInputStream()) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }
}