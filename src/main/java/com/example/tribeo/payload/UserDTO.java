package com.example.tribeo.payload;

import com.example.tribeo.model.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

	private String name;
	private Long userId;
	private String username;
	private String email;
	private String password;
	private Set<Role> roles = new HashSet<>();
}
