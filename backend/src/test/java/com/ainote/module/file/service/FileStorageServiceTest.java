package com.ainote.module.file.service;

import com.ainote.common.exception.BusinessException;
import com.ainote.module.file.mapper.FileInfoMapper;
import com.ainote.module.file.store.FileStore;
import com.ainote.module.file.vo.FileVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.verify;

/**
 * 文件服务：上传类型白名单 / 大小限制 / 存储落盘
 */
@ExtendWith(MockitoExtension.class)
class FileStorageServiceTest {

    @Mock
    private FileInfoMapper fileInfoMapper;
    @Mock
    private FileStore fileStore;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(fileInfoMapper, fileStore);
        // @Value 默认为 /files
        org.springframework.test.util.ReflectionTestUtils.setField(service, "accessPrefix", "/files");
    }

    @Test
    void rejectsDangerousExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "hack.exe",
                "application/octet-stream", new byte[]{1, 2, 3});
        assertThrows(BusinessException.class, () -> service.upload(1L, file));
    }

    @Test
    void rejectsOverSizeFile() {
        byte[] big = new byte[(int) (10 * 1024 * 1024L) + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.png", "image/png", big);
        assertThrows(BusinessException.class, () -> service.upload(1L, file));
    }

    @Test
    void uploadOkPersistsToStoreAndDb() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "pic.png", "image/png",
                "pngdata".getBytes(StandardCharsets.UTF_8));
        FileVO vo = service.upload(1L, file);

        assertNotNull(vo.getPath());
        org.junit.jupiter.api.Assertions.assertTrue(vo.getPath().startsWith("/files/"));
        assertEquals("image", vo.getType());
        verify(fileStore).save(matches("\\d{8}/[0-9a-f]{32}\\.png"), any(InputStream.class), anyLong(), anyString());
        verify(fileInfoMapper).insert(any());
    }
}