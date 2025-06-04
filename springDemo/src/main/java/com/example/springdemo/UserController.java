package com.example.springdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    // Inject UserService via constructor
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // HTTP GET /isActive?username=alice
    @GetMapping("/isActive")
    public String isUserActive(@RequestParam String username) {
        //return "hey!";
        boolean active = userService.isUserActive(username);
        return active ? "User is active" : "User is not active";
    }

    @GetMapping("/hi")
    public String hi(){
        return "hello!";
    }

}
