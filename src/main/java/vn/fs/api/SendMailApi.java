/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.repository.UserRepository;
import vn.fs.service.SendMailService;

@CrossOrigin("*")
@RestController
@RequestMapping("api/send-mail")
public class SendMailApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendMailApi.class);

	@Autowired
	SendMailService sendMail;

	@Autowired
	UserRepository Urepo;

	@Autowired
	ObjectMapper objectMapper;

	@PostMapping(value = "/otp", consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE, "application/json", "text/plain", "*/*" }, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> sendOpt(@RequestBody(required = false) Object request) {
		String email = null;
		try {
			LOGGER.info("Received OTP request, type: {}, value: {}", 
					request != null ? request.getClass().getName() : "null", request);
			
			// Handle null request
			if (request == null) {
				LOGGER.error("Request body is null");
				return ResponseEntity.badRequest().body("Email không được để trống");
			}
			
			// Handle both JSON object and plain string
			if (request instanceof String) {
				// Plain string - remove quotes if present
				email = ((String) request).replaceAll("^\"|\"$", "").trim();
			} else if (request instanceof Map) {
				// JSON object
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) request;
				if (map.containsKey("email")) {
					email = map.get("email").toString().trim();
				} else {
					LOGGER.error("Email field not found in request body");
					return ResponseEntity.badRequest().body("Email không được tìm thấy trong request");
				}
			} else {
				// Try to parse as JSON string or use toString
				String requestStr = request.toString();
				if (requestStr.startsWith("{") || requestStr.startsWith("\"")) {
					try {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = objectMapper.readValue(requestStr, Map.class);
						if (map.containsKey("email")) {
							email = map.get("email").toString().trim();
						} else {
							email = requestStr.replaceAll("^\"|\"$", "").trim();
						}
					} catch (Exception e) {
						LOGGER.warn("Failed to parse as JSON, using as string: {}", e.getMessage());
						email = requestStr.replaceAll("^\"|\"$", "").trim();
					}
				} else {
					email = requestStr.trim();
				}
			}

			if (email == null || email.isEmpty()) {
				LOGGER.error("Email is null or empty after parsing");
				return ResponseEntity.badRequest().body("Email không được để trống");
			}

			LOGGER.info("Attempting to send OTP to email: {}", email);

			// Validate email format
			if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
				LOGGER.error("Invalid email format: {}", email);
				return ResponseEntity.badRequest().body("Email không hợp lệ");
			}

			int random_otp = (int) Math.floor(Math.random() * (999999 - 100000 + 1) + 100000);

			if (Urepo.existsByEmail(email)) {
				LOGGER.warn("Email already exists: {}", email);
				return ResponseEntity.badRequest().body("Email đã được sử dụng");
			}

			sendMailOtp(email, random_otp, "Xác nhận tài khoản!");
			LOGGER.info("OTP queued for email: {}, OTP: {}", email, random_otp);
			return ResponseEntity.ok(random_otp);
		} catch (Exception e) {
			LOGGER.error("Error sending OTP to email: " + email, e);
			return ResponseEntity.status(500).body("Lỗi khi gửi OTP: " + e.getMessage());
		}
	}

	// sendmail
	public void sendMailOtp(String email, int Otp, String title) {
		try {
			String body = "<div>\r\n" + "        <h3>Mã OTP của bạn là: <span style=\"color:red; font-weight: bold;\">"
					+ Otp + "</span></h3>\r\n" + "    </div>";
			sendMail.queue(email, title, body);
			LOGGER.info("OTP email queued successfully for: {}", email);
		} catch (Exception e) {
			LOGGER.error("Failed to queue OTP email for: {}", email, e);
			throw e;
		}
	}

}