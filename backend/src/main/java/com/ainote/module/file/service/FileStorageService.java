package com.ainote.module.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.ainote.common.exception.BusinessException;
import com.ainote.module.file.entity.FileInfo;
import com.ainote.module.file.mapper.FileInfoMapper;
import com.ainote.module.file.store.FileStore;
import com.ainote.module.file.vo.FileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 文件服务（存储后端由 FileStore 抽象：本地磁盘 / MinIO）
 *
 * 统一对外 path 约定为 /files/{yyyyMMdd}/{storeName}，由 FileStore 决定实际落盘位置。
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileInfoMapper fileInfoMapper;
    private final FileStore fileStore;

    @Value("${file.access-prefix:/files}")
    private String accessPrefix;

    /** 允许预览的图片后缀 */
    private static final List<String> IMAGE_EXT = List.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg");

    /** 允许上传的文件类型白名单（图片 + 常用文档/压缩/音视频，拒绝可执行/脚本等危险类型） */
    private static final List<String> ALLOWED_EXT = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "md", "csv", "zip", "rar", "7z",
            "mp3", "mp4", "wav", "webm");

    /**
     * 上传文件
     */
    public FileVO upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        long max = 10 * 1024 * 1024L;
        if (file.getSize() > max) {
            throw new BusinessException("文件大小不能超过 10MB");
        }

        String original = file.getOriginalFilename();
        String ext = original != null && StrUtil.contains(original, ".")
                ? StrUtil.subAfter(original, ".", true).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("不支持的文件类型：" + (StrUtil.isBlank(ext) ? "无扩展名" : ext));
        }
        String storeName = IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext);

        // 按日期分目录，避免单目录文件过多；key 同时用于本地磁盘与 MinIO object key
        String dateDir = cn.hutool.core.date.DateUtil.format(
                new java.util.Date(), "yyyyMMdd");
        String key = dateDir + "/" + storeName;

        String path = StrUtil.removeSuffix(accessPrefix, "/") + "/" + key;
        try {
            fileStore.save(key, file.getInputStream(), file.getSize(),
                    file.getContentType() == null ? guessContentType(ext) : file.getContentType());
        } catch (Exception e) {
            throw new BusinessException("文件保存失败：" + e.getMessage());
        }

        FileInfo info = new FileInfo();
        info.setUserId(userId);
        info.setOriginalName(original);
        info.setStoreName(storeName);
        info.setPath(path);
        info.setSize(file.getSize());
        info.setType(IMAGE_EXT.contains(ext) ? "image" : "file");
        fileInfoMapper.insert(info);

        return toVO(info);
    }

    /**
     * 绑定文件到笔记
     */
    public void bindToNote(Long userId, Long fileId, Long noteId) {
        FileInfo info = getOwned(userId, fileId);
        info.setNoteId(noteId);
        fileInfoMapper.updateById(info);
    }

    /**
     * 查询文件列表（按用户，可按 noteId 过滤）
     */
    public List<FileVO> listByNote(Long userId, Long noteId) {
        return fileInfoMapper.selectList(
                        new LambdaQueryWrapper<FileInfo>()
                                .eq(FileInfo::getUserId, userId)
                                .eq(noteId != null, FileInfo::getNoteId, noteId)
                                .orderByDesc(FileInfo::getCreateTime))
                .stream().map(this::toVO).toList();
    }

    /**
     * 删除文件（逻辑删除 + 物理删除存储后端文件）
     */
    public void delete(Long userId, Long id) {
        FileInfo info = getOwned(userId, id);
        deleteStorage(info.getPath());
        fileInfoMapper.deleteById(info.getId());
    }

    /**
     * 鉴权下载：校验归属后返回原始文件名、MIME 与文件流
     */
    public FileDownload download(Long userId, Long id) {
        FileInfo info = getOwned(userId, id);
        String prefix = StrUtil.removeSuffix(accessPrefix, "/") + "/";
        String key = info.getPath().startsWith(prefix) ? info.getPath().substring(prefix.length()) : null;
        if (StrUtil.isBlank(key)) {
            throw new BusinessException("文件不可读取");
        }
        InputStream stream = fileStore.read(key);
        if (stream == null) {
            throw new BusinessException("文件已不存在");
        }
        FileDownload d = new FileDownload();
        d.setInputStream(stream);
        d.setContentType(fileStore.contentType(key));
        d.setOriginalName(info.getOriginalName());
        return d;
    }

    /** 下载结果载体 */
    @lombok.Getter
    @lombok.Setter
    @lombok.RequiredArgsConstructor
    public static class FileDownload {
        private java.io.InputStream inputStream;
        private String contentType;
        private String originalName;
    }

    /** 从对外 path 提取存储 key 并删除（幂等） */
    private void deleteStorage(String path) {
        if (StrUtil.isBlank(path)) return;
        String prefix = StrUtil.removeSuffix(accessPrefix, "/") + "/";
        String key = path.startsWith(prefix) ? path.substring(prefix.length()) : null;
        if (StrUtil.isNotBlank(key)) {
            fileStore.delete(key);
        }
    }

    private String guessContentType(String ext) {
        return IMAGE_EXT.contains(ext) ? "image/" + ext : "application/octet-stream";
    }

    private FileInfo getOwned(Long userId, Long id) {
        FileInfo info = fileInfoMapper.selectById(id);
        if (info == null || !info.getUserId().equals(userId)) {
            throw new BusinessException("文件不存在或无权访问");
        }
        return info;
    }

    private FileVO toVO(FileInfo info) {
        FileVO vo = new FileVO();
        BeanUtils.copyProperties(info, vo);
        return vo;
    }
}