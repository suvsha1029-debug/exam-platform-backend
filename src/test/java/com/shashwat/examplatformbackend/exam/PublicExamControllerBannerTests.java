package com.shashwat.examplatformbackend.exam;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicExamControllerBannerTests {
    @Test
    void uploadedBannersHaveImageContentTypes() {
        PortalBrandingStorageService storage = mock(PortalBrandingStorageService.class);
        PublicExamController controller = new PublicExamController(mock(ExamService.class), storage);
        Map<String, MediaType> cases = Map.of(
                "institute.png", MediaType.IMAGE_PNG,
                "institute.jpg", MediaType.IMAGE_JPEG,
                "institute.jpeg", MediaType.IMAGE_JPEG,
                "institute.webp", MediaType.parseMediaType("image/webp")
        );

        cases.forEach((filename, expectedType) -> {
            when(storage.loadPortalBanner("EXAM-1", filename))
                    .thenReturn(new ByteArrayResource(new byte[]{1}));
            assertEquals(expectedType,
                    controller.portalBanner("EXAM-1", filename).getHeaders().getContentType());
        });
    }
}
