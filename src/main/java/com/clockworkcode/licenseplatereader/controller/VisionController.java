package com.clockworkcode.licenseplatereader.controller;

import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.EntityAnnotation;
import com.google.cloud.vision.v1.Feature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.google.cloud.spring.vision.CloudVisionTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class VisionController {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private CloudVisionTemplate cloudVisionTemplate;

    @GetMapping("/extractText")
    public ResponseEntity<Map<String,String>> extractText(String imageUrl) throws IOException {
        Resource imageResource = this.resourceLoader.getResource("file:src/main/resources/20240513_193820.jpg");
        AnnotateImageResponse response = this.cloudVisionTemplate.analyzeImage(
                imageResource, Feature.Type.DOCUMENT_TEXT_DETECTION);

        Map<String,String> result = new HashMap<>();
        String licensePlate = response.getTextAnnotations(1).getDescription();
        result.put("licensePlate", licensePlate);

        return ResponseEntity.ok(result);
    }
}
