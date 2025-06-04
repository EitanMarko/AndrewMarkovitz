package com.example.springdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController //ADDED THIS ANNOTATION
public class HelloWorldController {

    @GetMapping("/helloWorld") //Added this annotation - maps HTTP GET requests to "/hello" to the "helloWorld() method
    public String helloWorld(){
        return "Hello World!";
    }

}
