package com.ainote.module.note.mapper;

import com.ainote.module.note.entity.Note;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 笔记 Mapper
 */
public interface NoteMapper extends BaseMapper<Note> {

    /** 浏览量原子自增（避免读-改-写并发丢失与全量字段回写） */
    @Update("UPDATE note SET view_count = view_count + 1 WHERE id = #{id}")
    int incrViewCount(@Param("id") Long id);
}