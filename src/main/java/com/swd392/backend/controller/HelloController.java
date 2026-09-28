package com.swd392.backend.controller;

import com.swd392.backend.dto.HelloResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping
    public HelloResponse hello() {
        return new HelloResponse("SWD392 backend is running");
    }
}
