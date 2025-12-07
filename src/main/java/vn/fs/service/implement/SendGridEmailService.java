package vn.fs.service.implement;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import vn.fs.dto.MailInfo;

/**
 * Alternative email service using SendGrid API (HTTP) instead of SMTP
 * This works better on Railway as it doesn't require SMTP port access
 */
@Service
public class SendGridEmailService {

	private static final Logger LOGGER = LoggerFactory.getLogger(SendGridEmailService.class);
	
	private final RestTemplate restTemplate = new RestTemplate();
	
	@Value("${sendgrid.api.key:}")
	private String sendGridApiKey;
	
	@Value("${sendgrid.from.email:nalumos2020@gmail.com}")
	private String fromEmail;
	
	@Value("${sendgrid.from.name:NaLumos Shop}")
	private String fromName;
	
	@Value("${sendgrid.enabled:false}")
	private boolean sendGridEnabled;
	
	private static final String SENDGRID_API_URL = "https://api.sendgrid.com/v3/mail/send";

	public boolean sendEmail(MailInfo mail) {
		LOGGER.info("SendGrid sendEmail called - enabled: {}, apiKey present: {}", 
				sendGridEnabled, sendGridApiKey != null && !sendGridApiKey.isEmpty());
		
		if (!sendGridEnabled) {
			LOGGER.warn("SendGrid is not enabled (sendgrid.enabled=false)");
			return false;
		}
		
		if (sendGridApiKey == null || sendGridApiKey.isEmpty()) {
			LOGGER.error("SendGrid API key is missing. Please set SENDGRID_API_KEY environment variable in Railway");
			return false;
		}
		
		try {
			LOGGER.info("Sending email via SendGrid to: {}", mail.getTo());
			
			Map<String, Object> payload = new HashMap<>();
			
			// From
			Map<String, String> from = new HashMap<>();
			from.put("email", fromEmail);
			from.put("name", fromName);
			payload.put("from", from);
			
			// To
			Map<String, String> to = new HashMap<>();
			to.put("email", mail.getTo());
			Map<String, Object> personalization = new HashMap<>();
			personalization.put("to", new Object[] { to });
			payload.put("personalizations", new Object[] { personalization });
			
			// Subject and content
			payload.put("subject", mail.getSubject());
			Map<String, String> content = new HashMap<>();
			content.put("type", "text/html");
			content.put("value", mail.getBody());
			payload.put("content", new Object[] { content });
			
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.setBearerAuth(sendGridApiKey);
			
			HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
			
			ResponseEntity<String> response = restTemplate.exchange(
				SENDGRID_API_URL, 
				HttpMethod.POST, 
				entity, 
				String.class
			);
			
			if (response.getStatusCode().is2xxSuccessful()) {
				LOGGER.info("Email sent successfully via SendGrid to: {}", mail.getTo());
				return true;
			} else {
				LOGGER.error("SendGrid API returned error: {} - {}", response.getStatusCode(), response.getBody());
				return false;
			}
		} catch (Exception e) {
			LOGGER.error("Failed to send email via SendGrid to {}: {}", mail.getTo(), e.getMessage(), e);
			return false;
		}
	}
}

