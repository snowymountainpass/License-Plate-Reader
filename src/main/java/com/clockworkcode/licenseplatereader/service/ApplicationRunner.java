package com.clockworkcode.licenseplatereader.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ApplicationRunner implements CommandLineRunner {

    @Autowired
    private VisionService visionService;

    @Override
    public void run(String... args) throws Exception {
        visionService.watchDirectory();
    }
}
