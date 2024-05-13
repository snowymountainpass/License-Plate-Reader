package com.clockworkcode.licenseplatereader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.vision.CloudVisionTemplate;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.Feature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Service
public class VisionService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private CloudVisionTemplate cloudVisionTemplate;

    public String extractText(String filename) throws IOException {

        Resource imageResource = this.resourceLoader.getResource("file:src/main/resources/filename");
        AnnotateImageResponse response = this.cloudVisionTemplate.analyzeImage(
                imageResource, Feature.Type.DOCUMENT_TEXT_DETECTION);

        return response.getTextAnnotations(1).getDescription();
    }

    public void processImage() throws IOException {
//        File[] files = new File("/src/main/resources/images").listFiles();

        Path directory = Paths.get("/src/main/resources/images");

        try{
            //create a WatchService
            WatchService watchService = FileSystems.getDefault().newWatchService();

            //register directory for ENTRY_CREATE events
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            //start to watch for directory events
            while (true) {
                WatchKey watchKey = watchService.take();

                // Process all events for the key
                for (WatchEvent<?> watchEvent : watchKey.pollEvents()) {
                    WatchEvent.Kind<?> kind = watchEvent.kind();

                    //handle ENTRY_CREATE event
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        // Get the filename of the newly created file
                        Path filename = (Path) watchEvent.context();

                        String licensePlate = extractText(filename.getFileName().toString());
                        String fileName = filename.getFileName().toString().replaceFirst("[.][^.]+$", "");

                        sendDetailsToEndpoint(licensePlate, fileName);

                    }
                }

            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }


    }

    public static void sendDetailsToEndpoint(String licensePlate, String filename) {
        try {

            URL url = new URL("http://localhost:8080/addTransaction");

            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");

            connection.setRequestProperty("Content-Type", "application/json");
            
            connection.setDoOutput(true);

            Map<String,String> result = new HashMap<>();
            result.put("licensePlate", licensePlate);
            result.put("airport", filename);

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = new ObjectMapper().writeValueAsBytes(result);
                outputStream.write(input);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
