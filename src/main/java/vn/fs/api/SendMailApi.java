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

	@PostMapping(value = "/otp", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<?> sendOpt(@RequestBody(required = false) Object request) {
		return sendOptInternal(request);
	}
	
	// Internal method to handle OTP sending
	private ResponseEntity<?> sendOptInternal(Object request) {
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
			String body = "<div style=\"font-family: Arial, sans-serif; padding: 20px;\">\r\n" 
					+ "        <h2 style=\"color: #333;\">Xác nhận tài khoản NaLumos Shop</h2>\r\n"
					+ "        <p>Xin chào,</p>\r\n"
					+ "        <p>Mã OTP của bạn là: <span style=\"color:red; font-weight: bold; font-size: 24px;\">"
					+ Otp + "</span></p>\r\n"
					+ "        <p>Mã này có hiệu lực trong 10 phút.</p>\r\n"
					+ "        <p>Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.</p>\r\n"
					+ "        <p>Trân trọng,<br>NaLumos Shop</p>\r\n"
					+ "    </div>";
			
			vn.fs.dto.MailInfo mailInfo = new vn.fs.dto.MailInfo(email, title, body);
			
			// Try to send immediately first, if fails then queue
			try {
				LOGGER.info("Attempting to send OTP email immediately to: {}", email);
				sendMail.send(mailInfo);
				LOGGER.info("OTP email sent immediately to: {}", email);
			} catch (javax.mail.MessagingException | java.io.IOException immediateError) {
				LOGGER.warn("Failed to send email immediately, queueing instead. Error: {}", immediateError.getMessage(), immediateError);
				sendMail.queue(mailInfo);
				LOGGER.info("OTP email queued for: {}", email);
			} catch (Exception immediateError) {
				LOGGER.warn("Unexpected error sending email immediately, queueing instead. Error: {}", immediateError.getMessage(), immediateError);
				sendMail.queue(mailInfo);
				LOGGER.info("OTP email queued for: {}", email);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to send/queue OTP email for: {}", email, e);
			throw e;
		}
	}

}