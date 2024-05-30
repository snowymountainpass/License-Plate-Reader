package com.clockworkcode.licenseplatereader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.vision.CloudVisionTemplate;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.Feature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

@Service
public class VisionService {

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private CloudVisionTemplate cloudVisionTemplate;

    static Logger logger = LoggerFactory.getLogger(VisionService.class);

    private Path getImagesPath() throws IOException, URISyntaxException {

        Resource resource = resourceLoader.getResource("classpath:static/images");

        // Check if resource is a directory
        if (resource.exists() && resource.isFile()) {
            try {
                return Paths.get(resource.getFile().toURI()).toRealPath();
            } catch (IOException e) {
                // Fallback: get the resource URL and convert to a path
                return Paths.get(resource.getURL().toURI());
            }
        } else {
            // Fallback to file system path for development
            String relativePath = "src/main/resources/static/images";
            return Paths.get(relativePath).toRealPath();
        }
    }

    public void watchDirectory() {
        try {
            Path directory = getImagesPath();
            WatchService watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            Runnable watchTask = () -> {
                while (true) {
                    WatchKey watchKey;
                    try {
                        watchKey = watchService.take();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }

                    for (WatchEvent<?> event : watchKey.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();

                        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path path = ev.context();
                            String fileName = path.getFileName().toString().replaceFirst("[.][^.]+$", "");

                            try {
                                String licensePlate = extractText(path.getFileName().toString());
                                sendDetailsToEndpoint(licensePlate, fileName,directory);
                            } catch (IOException | URISyntaxException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    boolean valid = watchKey.reset();
                    if (!valid) {
                        break;
                    }
                }
            };

            Thread watchThread = new Thread(watchTask);
            watchThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public String extractText(String filename) throws IOException, URISyntaxException {

        Path imagesPath = getImagesPath();
        String location = imagesPath.toUri().toString();

        Resource imageResource = this.resourceLoader.getResource(location.concat(filename));
        AnnotateImageResponse response = this.cloudVisionTemplate.analyzeImage(
                imageResource, Feature.Type.DOCUMENT_TEXT_DETECTION);

        return response.getTextAnnotations(1).getDescription();
    }

    public static void sendDetailsToEndpoint(String licensePlate, String filename,Path path) {
        try {

            URL url = new URL("http://localhost:8080/transaction/addTransaction");

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
            logger.info("Response Code: {}", responseCode);
            //If response ok (200) => we delete the file
            if(responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println("Successfully sent details to endpoint");
                logger.info("Successfully sent details to endpoint");
                Files.delete(Path.of(path.toString()+ "\\" +filename+".jpg"));
            }
            connection.disconnect();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
