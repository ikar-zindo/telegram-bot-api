package com.telegrambotapi.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
