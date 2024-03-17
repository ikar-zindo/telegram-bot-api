package com.telegrambotapi;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

@EnableScheduling
@SpringBootApplication
public class TelegramBotApiApplication {

	public static void main(String[] args) throws IOException {
      ClassLoader classLoader = TelegramBotApiApplication.class.getClassLoader();

      File file = new File(Objects.requireNonNull(classLoader.getResource("path/to/serviceAccountKey.json")).getFile());
      FileInputStream serviceAccount =
              new FileInputStream(file.getAbsoluteFile());

      FirebaseOptions options = new FirebaseOptions.Builder()
              .setCredentials(GoogleCredentials.fromStream(serviceAccount))
              .build();

      FirebaseApp.initializeApp(options);

		SpringApplication.run(TelegramBotApiApplication.class, args);
	}
}
