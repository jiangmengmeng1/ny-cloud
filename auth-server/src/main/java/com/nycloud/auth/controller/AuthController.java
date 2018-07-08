/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nycloud.auth.controller;
import com.nycloud.auth.model.Login;
import com.nycloud.auth.model.UserDetails;
import com.nycloud.auth.service.JdbcUserDetailsService;
import com.nycloud.common.jwt.JwtEntity;
import com.nycloud.common.jwt.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class AuthController {

	@Autowired
	private JdbcUserDetailsService userDetailsService;

	@Autowired
	RedisTemplate<String, Object> redisTemplate;

	@PostMapping("/login")
	public ResponseEntity login(@Validated @RequestBody Login login, BindingResult bindingResult) {
		if (bindingResult.hasErrors() ) {
			return new ResponseEntity(HttpStatus.BAD_REQUEST);
		}
		UserDetails userDetails = userDetailsService.loadUserDetailsByUserName(login.getUsername());
		if (!userDetails.isEnabled()) {
			return new ResponseEntity<String>("账号不可用", HttpStatus.FORBIDDEN);
		}
		if (userDetails != null && userDetails.getPassword().equals(login.getPassword())) {
			// 获取用户的角色
			Map<String, Object> map = new HashMap<>();
			if (redisTemplate.hasKey(userDetails.getUserId())) {
				map = (Map<String, Object>) redisTemplate.opsForValue().get(userDetails.getUserId());
			} else {
				List<String> roles = userDetailsService.loadUserRolesByUserId(Long.valueOf(userDetails.getUserId()));
				map.put("userId", userDetails.getUserId());
				map.put("username", userDetails.getUsername());
				map.put("enabled", userDetails.isEnabled());
				map.put("roles", String.join(",", roles));
				redisTemplate.opsForValue().set(userDetails.getUserId(), map);
			}
			String token = JwtUtil.generateToken(map);
			return new ResponseEntity<String>(token, HttpStatus.OK);
		}
		return new ResponseEntity<String>("用户名或密码错误", HttpStatus.FORBIDDEN);
	}

	@PostMapping("/logout")
	public ResponseEntity loginOut(@RequestHeader(value = "Authorization", required = true) String Authorization) {
		if (JwtUtil.isJwtBearerToken(Authorization)) {
			JwtEntity jwtEntity = JwtUtil.parseToken(Authorization);
			if (redisTemplate.hasKey(jwtEntity.getUserId())) {
				// 登出后清楚缓存
				redisTemplate.delete(jwtEntity.getUserId());
			}
			return ResponseEntity.ok().build();
		} else {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
	}

	@GetMapping("/check_token")
	public Map<String, ?> checkToken(@RequestParam(value = "token", required = true) String token) {
		Map<String, Object> response = new HashMap<>(1);
		response.put("active", true);
		return response;
	}



}
