package com.example.springdemo;

import org.springframework.stereotype.Service;

@Service //MUST USE "SERVICE" ANNOTATION
public class UserService {

    // This is your "business logic" method
    public boolean isUserActive(String username) {
        // Dummy logic: users with names longer than 5 chars are "active"
        return username != null && username.length() > 5;
    }

}
