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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
	
	@org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
	private String senderEmail;
	
	@org.springframework.beans.factory.annotation.Value("${app.mail.sender-name:NaLumos Shop}")
	private String senderName;

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
			String logoUrl = "https://nalumosshop-web-production.up.railway.app/img/logo.png";
			String body = "<div style=\"font-family: Arial, sans-serif; padding: 20px; max-width: 600px; margin: 0 auto; background-color: #f9f9f9;\">\r\n"
					+ "        <div style=\"text-align: center; margin-bottom: 30px;\">\r\n"
					+ "            <img src=\"" + logoUrl + "\" alt=\"NaLumos Shop\" style=\"max-width: 200px; height: auto;\" />\r\n"
					+ "        </div>\r\n"
					+ "        <div style=\"background-color: #ffffff; padding: 30px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);\">\r\n"
					+ "            <h2 style=\"color: #333; margin-top: 0;\">Xác nhận tài khoản NaLumos Shop</h2>\r\n"
					+ "            <p style=\"color: #666; font-size: 16px;\">Xin chào,</p>\r\n"
					+ "            <p style=\"color: #666; font-size: 16px;\">Mã OTP của bạn là:</p>\r\n"
					+ "            <div style=\"text-align: center; margin: 30px 0; padding: 20px; background-color: #f5f5f5; border-radius: 5px;\">\r\n"
					+ "                <span style=\"color: #e74c3c; font-weight: bold; font-size: 32px; letter-spacing: 5px;\">"
					+ Otp + "</span>\r\n"
					+ "            </div>\r\n"
					+ "            <p style=\"color: #666; font-size: 14px;\">Mã này có hiệu lực trong 10 phút.</p>\r\n"
					+ "            <p style=\"color: #999; font-size: 12px; margin-top: 30px;\">Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email này.</p>\r\n"
					+ "            <p style=\"color: #333; font-size: 16px; margin-top: 30px;\">Trân trọng,<br><strong>NaLumos Shop</strong></p>\r\n"
					+ "        </div>\r\n"
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

	// Test endpoint to check email configuration
	@GetMapping("/test")
	public ResponseEntity<?> testEmail(@RequestParam(required = false) String email) {
		return testEmailInternal(email);
	}
	
	// Also allow GET on /otp for testing (will return info message)
	@GetMapping("/otp")
	public ResponseEntity<?> getOtpInfo(@RequestParam(required = false) String email) {
		if (email != null && !email.isEmpty()) {
			// If email provided, try to send test OTP
			LOGGER.info("GET request to /otp with email: {}", email);
			return testEmailInternal(email);
		}
		return ResponseEntity.ok("OTP endpoint - Use POST method with JSON body: {\"email\": \"your-email@example.com\"}");
	}
	
	private ResponseEntity<?> testEmailInternal(String email) {
		try {
			LOGGER.info("Testing email configuration...");
			
			if (email == null || email.isEmpty()) {
				email = "test@example.com"; // Default test email
			}
			
			LOGGER.info("Sender email: {}", senderEmail);
			LOGGER.info("Sender name: {}", senderName);
			LOGGER.info("Testing email to: {}", email);
			
			// Try to send a test email
			String testBody = "<div style=\"font-family: Arial, sans-serif; padding: 20px;\">"
					+ "<h2>Test Email from NaLumos Backend</h2>"
					+ "<p>This is a test email to verify email configuration.</p>"
					+ "<p>If you receive this, email service is working correctly.</p>"
					+ "</div>";
			
			vn.fs.dto.MailInfo testMail = new vn.fs.dto.MailInfo(email, "Test Email - NaLumos", testBody);
			
			try {
				sendMail.send(testMail);
				LOGGER.info("Test email sent successfully to: {}", email);
				return ResponseEntity.ok("Test email sent successfully to: " + email);
			} catch (Exception e) {
				LOGGER.error("Failed to send test email: {}", e.getMessage(), e);
				return ResponseEntity.status(500).body("Failed to send test email: " + e.getMessage() + "\nStack trace: " + e.getClass().getName());
			}
		} catch (Exception e) {
			LOGGER.error("Error in test endpoint: {}", e.getMessage(), e);
			return ResponseEntity.status(500).body("Error: " + e.getMessage());
		}
	}

}