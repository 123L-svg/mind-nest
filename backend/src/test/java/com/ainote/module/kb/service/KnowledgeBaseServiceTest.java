package com.ainote.module.kb.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.module.kb.entity.KnowledgeBase;
import com.ainote.module.kb.mapper.KnowledgeBaseMapper;
import com.ainote.module.kb.vo.KbVO;
import com.ainote.module.note.mapper.NoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 知识库：公开分享内部防御
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock
    private KnowledgeBaseMapper kbMapper;
    @Mock
    private NoteMapper noteMapper;

    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseService(kbMapper, noteMapper);
    }

    @Test
    void publicDetailAllowedWhenPublic() {
        KnowledgeBase kb = kb(1L, 1);
        when(kbMapper.selectById(1L)).thenReturn(kb);
        when(noteMapper.selectCount(any())).thenReturn(0L);

        KbVO vo = service.publicDetail(1L);
        assertNotNull(vo);
    }

    @Test
    void publicDetailRejectsPrivateKb() {
        when(kbMapper.selectById(1L)).thenReturn(kb(1L, 0));
        assertThrows(BusinessException.class, () -> service.publicDetail(1L));
    }

    @Test
    void publicDetailRejectsMissingKb() {
        when(kbMapper.selectById(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.publicDetail(1L));
    }

    private KnowledgeBase kb(Long id, Integer isPublic) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setIsPublic(isPublic);
        return kb;
    }
}