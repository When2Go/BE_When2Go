package org.example.when2go.domain.trip.service;

import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.when2go.domain.trip.client.GeminiAudioParseClient;
import org.example.when2go.domain.trip.dto.NavigationParseResponse;
import org.example.when2go.domain.trip.error.NavigationParseErrorCode;
import org.example.when2go.global.error.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class NavigationParseService {

    private final GeminiAudioParseClient geminiAudioParseClient;

    private static final Map<String, String> MIME_MAP = Map.of(
            "mp3", "audio/mp3",
            "wav", "audio/wav",
            "m4a", "audio/mp4",
            "aac", "audio/aac",
            "flac", "audio/flac",
            "ogg", "audio/ogg"
    );

    public NavigationParseResponse parse(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        byte[] audioBytes;
        try {
            audioBytes = audio.getBytes();
        } catch (IOException e) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO, e);
        }

        String mimeType = resolveMimeType(audio.getOriginalFilename());
        return geminiAudioParseClient.parse(audioBytes, mimeType);
    }

    private String resolveMimeType(String filename) {
        if (filename == null) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new DomainException(NavigationParseErrorCode.INVALID_AUDIO);
        }

        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return MIME_MAP.getOrDefault(ext, "audio/" + ext);
    }
}
