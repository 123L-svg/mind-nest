package com.ainote.module.file.store;

import java.io.InputStream;

/**
 * 文件存储抽象：本地磁盘 / MinIO 对象存储统一接口。
 * <p>path 约定统一为 /files/{yyyyMMdd}/{storeName}，对外访问路径与存储后端解耦。
 */
public interface FileStore {

    /**
     * 保存文件
     *
     * @param key        存储键（如 20260827/uuid.png）
     * @param stream     文件流
     * @param size       字节数
     * @param contentType MIME 类型
     */
    void save(String key, InputStream stream, long size, String contentType);

    /**
     * 删除文件（幂等，不存在不报错）
     *
     * @param key 存储键
     */
    void delete(String key);

    /** 读取文件流（不存在返回 null），供下载接口使用 */
    default InputStream read(String key) {
        return null;
    }

    /** 读取 MIME 类型（未知返回 null） */
    default String contentType(String key) {
        return null;
    }

    /** 是否启用当前实现（供访问代理判断） */
    default boolean enabled() {
        return true;
    }
}