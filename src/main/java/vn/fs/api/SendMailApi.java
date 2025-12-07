/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	@PostMapping("/otp")
	public ResponseEntity<?> sendOpt(@RequestBody String email) {
		try {
			if (email == null || email.trim().isEmpty()) {
				LOGGER.error("Email is null or empty");
				return ResponseEntity.badRequest().body("Email không được để trống");
			}
			
			LOGGER.info("Attempting to send OTP to email: {}", email);
			
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