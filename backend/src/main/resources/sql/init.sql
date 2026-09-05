-- =====================================================================
-- MindNest（智巢）· AI 智能笔记与知识库系统 - 数据库初始化脚本
-- 执行方式: mysql -uroot -p < init.sql
-- 字符集统一 utf8mb4，MyBatis-Plus 默认雪花 ID、逻辑删除字段 deleted
-- =====================================================================

CREATE DATABASE IF NOT EXISTS ai_note DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE ai_note;

-- -----------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL COMMENT '用户ID（雪花）',
    `username`    VARCHAR(32)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(128) DEFAULT NULL COMMENT '密码（BCrypt 加密；第三方登录用户为空）',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    `email`       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    `oauth_type`  VARCHAR(16)  DEFAULT NULL COMMENT '第三方来源：github/gitee 等，空表示账号密码用户',
    `open_id`     VARCHAR(64)  DEFAULT NULL COMMENT '第三方平台唯一标识',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '账号状态 0正常 1禁用',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_oauth` (`oauth_type`, `open_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- -----------------------------------------------------------
-- 2. 知识库表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_base` (
    `id`          BIGINT       NOT NULL COMMENT '知识库ID',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '知识库名称',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '知识库描述',
    `cover`       VARCHAR(255) DEFAULT NULL COMMENT '封面图',
    `is_public`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否公开 0私有 1公开',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- -----------------------------------------------------------
-- 3. 分类表（挂在知识库下）
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
    `id`          BIGINT       NOT NULL COMMENT '分类ID',
    `user_id`     BIGINT       NOT NULL COMMENT '用户ID',
    `kb_id`       BIGINT       NOT NULL COMMENT '所属知识库ID',
    `name`        VARCHAR(64)  NOT NULL COMMENT '分类名称',
    `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类表';

-- -----------------------------------------------------------
-- 4. 标签表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tag` (
    `id`          BIGINT      NOT NULL COMMENT '标签ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `name`        VARCHAR(32) NOT NULL COMMENT '标签名称',
    `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- -----------------------------------------------------------
-- 5. 笔记表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note` (
    `id`          BIGINT        NOT NULL COMMENT '笔记ID',
    `user_id`     BIGINT        NOT NULL COMMENT '用户ID',
    `kb_id`       BIGINT        NOT NULL COMMENT '所属知识库ID',
    `category_id` BIGINT        DEFAULT NULL COMMENT '分类ID',
    `title`       VARCHAR(128)  NOT NULL COMMENT '笔记标题',
    `content`     LONGTEXT      COMMENT '笔记内容（富文本/Html）',
    `summary`     VARCHAR(512)  DEFAULT NULL COMMENT '笔记摘要',
    `word_count`  INT           NOT NULL DEFAULT 0 COMMENT '字数统计',
    `view_count`  INT           NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `status`      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态 0正常 1回收站',
    `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记表';

-- -----------------------------------------------------------
-- 6. 笔记-标签关联表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_tag` (
    `id`      BIGINT NOT NULL COMMENT '主键',
    `note_id` BIGINT NOT NULL COMMENT '笔记ID',
    `tag_id`  BIGINT NOT NULL COMMENT '标签ID',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    PRIMARY KEY (`id`),
    KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记标签关联表';

-- -----------------------------------------------------------
-- 7. 文件信息表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `file_info` (
    `id`          BIGINT       NOT NULL COMMENT '文件ID',
    `user_id`     BIGINT       NOT NULL COMMENT '上传者用户ID',
    `note_id`     BIGINT       DEFAULT NULL COMMENT '关联笔记ID（可空）',
    `originalName` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    `storeName`   VARCHAR(255) NOT NULL COMMENT '存储文件名',
    `path`        VARCHAR(512) NOT NULL COMMENT '访问路径',
    `size`        BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `type`        VARCHAR(16)  DEFAULT NULL COMMENT '文件类型 image/file',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件信息表';

-- -----------------------------------------------------------
-- 8. 操作日志表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `oper_log` (
    `id`          BIGINT       NOT NULL COMMENT '日志ID',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户ID',
    `module`      VARCHAR(32)  DEFAULT NULL COMMENT '模块',
    `action`      VARCHAR(64)  DEFAULT NULL COMMENT '操作描述',
    `method`      VARCHAR(128) DEFAULT NULL COMMENT '请求方法',
    `params`      TEXT         COMMENT '请求参数',
    `ip`          VARCHAR(64)  DEFAULT NULL COMMENT 'IP地址',
    `duration`    BIGINT       DEFAULT 0 COMMENT '耗时(ms)',
    `success`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功 0否 1是',
    `error_msg`   TEXT         COMMENT '异常信息',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- -----------------------------------------------------------
-- 9. 笔记版本历史表
-- -----------------------------------------------------------
CREATE TABLE IF NOT EXISTS `note_version` (
    `id`          BIGINT        NOT NULL COMMENT '版本ID',
    `note_id`     BIGINT        NOT NULL COMMENT '笔记ID',
    `title`       VARCHAR(128)  DEFAULT NULL COMMENT '当时标题',
    `content`     LONGTEXT      COMMENT '当时内容',
    `summary`     VARCHAR(512)  DEFAULT NULL COMMENT '当时摘要',
    `word_count`  INT           NOT NULL DEFAULT 0 COMMENT '当时字数',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '保存/版本时间',
    PRIMARY KEY (`id`),
    KEY `idx_note_id` (`note_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记版本历史表';

-- -----------------------------------------------------------
-- 10. 已有库升级（第三方登录）：对非全新初始化重复执行无效，可安全运行
-- -----------------------------------------------------------
ALTER TABLE `sys_user`
    MODIFY COLUMN `password` VARCHAR(128) DEFAULT NULL COMMENT '密码（BCrypt 加密；第三方登录用户为空）',
    ADD COLUMN `oauth_type` VARCHAR(16) DEFAULT NULL COMMENT '第三方来源：github/gitee 等，空表示账号密码用户' AFTER `email`,
    ADD COLUMN `open_id` VARCHAR(64) DEFAULT NULL COMMENT '第三方平台唯一标识' AFTER `oauth_type`,
    ADD UNIQUE KEY `uk_oauth` (`oauth_type`, `open_id`);