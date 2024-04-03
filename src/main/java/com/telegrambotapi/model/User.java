package com.telegrambotapi.model;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class User {

   private Long userId;

   private String name;

   private String userBirthday;

   private Date createdAt;
}
