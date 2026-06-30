package org.example.when2go.domain.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import org.example.when2go.domain.trip.dto.AudioParseRequest;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.global.error.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class NavigationParseServiceTest {

    @Mock
    private NavigationParseBatchProcessor processor;

    @InjectMocks
    private NavigationParseService navigationParseService;

    @Test
    void 빈_파일이면_예외() {
        MockMultipartFile empty = new MockMultipartFile("audio", "voice.mp3", "audio/mp3", new byte[0]);

        assertThatThrownBy(() -> navigationParseService.parse(empty))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void 확장자가_없으면_예외() {
        MockMultipartFile noExt = new MockMultipartFile(
                "audio", "voicefile", "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> navigationParseService.parse(noExt))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void m4a_확장자는_audio_mp4로_매핑되어_전달된다() {
        MockMultipartFile m4a = new MockMultipartFile("audio", "voice.m4a", "audio/mp4", new byte[]{1, 2, 3});
        when(processor.submit(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new NavigationParseResponse(null, "강남역", "2026-06-20 14:00")));

        navigationParseService.parse(m4a);

        ArgumentCaptor<AudioParseRequest> captor = ArgumentCaptor.forClass(AudioParseRequest.class);
        verify(processor).submit(captor.capture());
        assertThat(captor.getValue().mimeType()).isEqualTo("audio/mp4");
        assertThat(captor.getValue().audioBytes()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void 매핑에_없는_확장자는_audio_확장자_형태로_전달된다() {
        MockMultipartFile opus = new MockMultipartFile("audio", "voice.opus", "audio/opus", new byte[]{1, 2, 3});
        when(processor.submit(any()))
                .thenReturn(CompletableFuture.completedFuture(
                        new NavigationParseResponse(null, "강남역", null)));

        navigationParseService.parse(opus);

        ArgumentCaptor<AudioParseRequest> captor = ArgumentCaptor.forClass(AudioParseRequest.class);
        verify(processor).submit(captor.capture());
        assertThat(captor.getValue().mimeType()).isEqualTo("audio/opus");
    }

    @Test
    void Processor가_실패하면_DomainException으로_변환된다() {
        MockMultipartFile mp3 = new MockMultipartFile("audio", "voice.mp3", "audio/mp3", new byte[]{1, 2, 3});
        CompletableFuture<NavigationParseResponse> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("처리 대기열이 가득 찼습니다"));
        when(processor.submit(any())).thenReturn(failed);

        assertThatThrownBy(() -> navigationParseService.parse(mp3))
                .isInstanceOf(DomainException.class);
    }
}
