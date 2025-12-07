/*
 * (C) Copyright 2022. All Rights Reserved.
 *
 * @author DongTHD
 * @date Mar 10, 2022
*/
package vn.fs.service.implement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import vn.fs.dto.MailInfo;
import vn.fs.service.SendMailService;

@Service
public class SendMailServiceImplement implements SendMailService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendMailServiceImplement.class);

	@Autowired
	JavaMailSender sender;

	@Value("${spring.mail.username}")
	private String senderEmail;

	@Value("${app.mail.sender-name:NaLumos Shop}")
	private String senderName;

	List<MailInfo> list = new ArrayList<>();

	@Override
	public void send(MailInfo mail) throws MessagingException, IOException {
		LOGGER.info("Preparing to send email to: {}, subject: {}", mail.getTo(), mail.getSubject());
		
		// Validate email configuration
		if (senderEmail == null || senderEmail.isEmpty()) {
			LOGGER.error("Sender email is not configured");
			throw new MessagingException("Email sender is not configured");
		}
		
		// Tạo message
		MimeMessage message = sender.createMimeMessage();
		// Sử dụng Helper để thiết lập các thông tin cần thiết cho message
		MimeMessageHelper helper = new MimeMessageHelper(message, true, "utf-8");
		String fromAddress = mail.getFrom();
		if (!StringUtils.hasText(fromAddress)) {
			fromAddress = String.format("%s <%s>", senderName, senderEmail);
		}
		helper.setFrom(fromAddress);
		helper.setTo(mail.getTo());
		helper.setSubject(mail.getSubject());
		helper.setText(mail.getBody(), true);
		helper.setReplyTo(fromAddress);

		if (mail.getAttachments() != null) {
			FileSystemResource file = new FileSystemResource(new File(mail.getAttachments()));
			helper.addAttachment(mail.getAttachments(), file);
		}

		// Gửi message đến SMTP server
		LOGGER.info("Sending email to: {}, from: {}", mail.getTo(), fromAddress);
		try {
			sender.send(message);
			LOGGER.info("Email sent successfully to: {}", mail.getTo());
		} catch (Exception e) {
			LOGGER.error("Exception while sending email to {}: {}", mail.getTo(), e.getMessage(), e);
			throw e;
		}
	}

	@Override
	public void queue(MailInfo mail) {
		LOGGER.info("Queueing email to: {}, subject: {}", mail.getTo(), mail.getSubject());
		list.add(mail);
		LOGGER.info("Email queued. Queue size: {}", list.size());
	}

	@Override
	public void queue(String to, String subject, String body) {
		LOGGER.info("Queueing email to: {}, subject: {}", to, subject);
		queue(new MailInfo(to, subject, body));
	}

	@Override
	@Scheduled(fixedDelay = 5000)
	public void run() {
		if (list.isEmpty()) {
			return;
		}
		
		LOGGER.info("Processing email queue. Size: {}", list.size());
		while (!list.isEmpty()) {
			MailInfo mail = list.remove(0);
			try {
				this.send(mail);
				LOGGER.info("Email sent successfully to: {}", mail.getTo());
			} catch (MessagingException e) {
				LOGGER.error("Failed to send email to {}: {}", mail.getTo(), e.getMessage(), e);
				// Re-queue the email for retry (optional - you might want to limit retries)
				// list.add(mail);
			} catch (IOException e) {
				LOGGER.error("IO error sending email to {}: {}", mail.getTo(), e.getMessage(), e);
			} catch (Exception e) {
				LOGGER.error("Unexpected error sending email to {}: {}", mail.getTo(), e.getMessage(), e);
			}
		}
		LOGGER.info("Email queue processing completed");
	}

}
