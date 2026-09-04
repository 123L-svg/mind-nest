package com.ainote.module.note.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.ainote.module.note.dto.NoteDTO;
import com.ainote.module.note.entity.Note;
import com.ainote.module.note.entity.NoteVersion;
import com.ainote.module.note.mapper.NoteMapper;
import com.ainote.module.note.mapper.NoteTagMapper;
import com.ainote.module.note.mapper.NoteVersionMapper;
import com.ainote.module.note.vo.NoteVersionVO;
import com.ainote.module.search.service.NoteSearchService;
import com.ainote.module.tag.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 笔记服务：回收站（移入/恢复/永久删除）与版本历史（保存/明细/回滚）
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteMapper noteMapper;
    @Mock private NoteTagMapper noteTagMapper;
    @Mock private TagMapper tagMapper;
    @Mock private KnowledgeBaseMapper kbMapper;
    @Mock private NoteSearchService noteSearchService;
    @Mock private NoteVersionMapper noteVersionMapper;

    private NoteService service;
    private final Long userId = 1L;
    private Note ownedNote;

    @BeforeEach
    void setUp() {
        service = new NoteService(noteMapper, noteTagMapper, tagMapper, kbMapper, noteSearchService, noteVersionMapper);
        ReflectionTestUtils.setField(service, "searchEngine", "elasticsearch");
        ownedNote = new Note();
        ownedNote.setId(10L);
        ownedNote.setUserId(userId);
        ownedNote.setTitle("标题");
        ownedNote.setContent("<p>内容</p>");
        ownedNote.setStatus(0);
    }

    // ---------------- 回收站 ----------------

    @Test
    void deleteMovesToRecycleAndRemovesIndex() {
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);

        service.delete(userId, 10L);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getStatus());
        verify(noteSearchService).deleteById(10L);
    }

    @Test
    void restoreResetsStatusAndIndexes() {
        ownedNote.setStatus(1);
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);

        service.restore(userId, 10L);

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(noteSearchService).save(any(Note.class));
    }

    @Test
    void permanentlyDeletePurgesTagsAndIndex() {
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);

        service.permanentlyDelete(userId, 10L);

        verify(noteTagMapper).delete(any());
        verify(noteMapper).deleteById(10L);
        verify(noteSearchService).deleteById(10L);
    }

    @Test
    void deleteRejectsOtherUsersNote() {
        Note other = new Note();
        other.setId(99L);
        other.setUserId(2L);
        when(noteMapper.selectById(99L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.delete(userId, 99L));
    }

    // ---------------- 回收站自动清理 ----------------

    @Test
    void cleanupPurgesExpiredRecycleNotes() {
        Note expired = new Note();
        expired.setId(20L);
        expired.setUserId(userId);
        expired.setStatus(1);
        expired.setUpdateTime(LocalDateTime.now().minusDays(40));
        when(noteMapper.selectList(any())).thenReturn(List.of(expired));

        int n = service.cleanupRecycleBin(30);

        assertEquals(1, n);
        verify(noteTagMapper).delete(any());
        verify(noteMapper).delete(any());
        verify(noteSearchService).deleteById(20L);
    }

    @Test
    void cleanupDoesNothingWhenNoneExpired() {
        when(noteMapper.selectList(any())).thenReturn(Collections.emptyList());

        int n = service.cleanupRecycleBin(30);

        assertEquals(0, n);
        verify(noteTagMapper, never()).delete(any());
        verify(noteMapper, never()).delete(any());
        verify(noteSearchService, never()).deleteById(any());
    }

    // ---------------- 版本历史 ----------------

    @Test
    void updateRecordsVersionSnapshot() {
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);
        when(noteVersionMapper.selectList(any())).thenReturn(Collections.emptyList());

        NoteDTO dto = new NoteDTO();
        dto.setId(10L);
        dto.setTitle("新标题");
        dto.setContent("<p>新内容</p>");
        dto.setTagIds(Collections.emptyList());
        service.update(userId, dto);

        ArgumentCaptor<NoteVersion> vCaptor = ArgumentCaptor.forClass(NoteVersion.class);
        verify(noteVersionMapper).insert(vCaptor.capture());
        assertEquals(10L, vCaptor.getValue().getNoteId());
        assertEquals("<p>新内容</p>", vCaptor.getValue().getContent());
    }

    @Test
    void versionDetailReturnsVersionForOwnedNote() {
        NoteVersion v = new NoteVersion();
        v.setId(5L);
        v.setNoteId(10L);
        v.setContent("<p>old</p>");
        when(noteVersionMapper.selectById(5L)).thenReturn(v);
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);

        NoteVersion got = service.versionDetail(userId, 5L);
        assertEquals(5L, got.getId());
    }

    @Test
    void versionDetailRejectsOthersVersion() {
        NoteVersion v = new NoteVersion();
        v.setId(5L);
        v.setNoteId(99L); // 属于他人
        when(noteVersionMapper.selectById(5L)).thenReturn(v);
        Note other = new Note();
        other.setId(99L);
        other.setUserId(2L);
        when(noteMapper.selectById(99L)).thenReturn(other);

        assertThrows(BusinessException.class, () -> service.versionDetail(userId, 5L));
    }

    @Test
    void rollbackRestoresFieldsAndRecordsNewVersion() {
        NoteVersion v = new NoteVersion();
        v.setId(5L);
        v.setNoteId(10L);
        v.setTitle("旧标题");
        v.setContent("<p>旧内容</p>");
        v.setSummary("旧摘要");
        v.setWordCount(20);
        when(noteVersionMapper.selectById(5L)).thenReturn(v);
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);
        when(noteVersionMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.rollback(userId, 5L);

        ArgumentCaptor<Note> noteCaptor = ArgumentCaptor.forClass(Note.class);
        verify(noteMapper).updateById(noteCaptor.capture());
        assertEquals("旧标题", noteCaptor.getValue().getTitle());
        verify(noteSearchService).save(any(Note.class));
        verify(noteVersionMapper).insert(any(NoteVersion.class));
    }

    @Test
    void versionsListsWithoutContent() {
        when(noteMapper.selectById(10L)).thenReturn(ownedNote);
        NoteVersion v = new NoteVersion();
        v.setId(5L);
        v.setNoteId(10L);
        v.setTitle("旧");
        v.setContent("<p>不应出现在列表</p>");
        when(noteVersionMapper.selectList(any())).thenReturn(List.of(v));

        List<NoteVersionVO> vos = service.versions(userId, 10L);
        assertEquals(1, vos.size());
        assertEquals("旧", vos.get(0).getTitle());
        // NoteVersionVO 列表不携带 content 大字段（脱敏），仅基础元信息
        org.junit.jupiter.api.Assertions.assertNull(vos.get(0).getSummary());
    }
}