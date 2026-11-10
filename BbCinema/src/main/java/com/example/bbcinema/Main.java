package com.example.bbcinema;

import com.example.bbcinema.config.BrowserLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        var context = SpringApplication.run(Main.class, args);
        BrowserLauncher.openLoginPage(context);
    }
}
