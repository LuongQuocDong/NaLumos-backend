package vn.fs.api;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.dto.MomoPaymentRequest;

@CrossOrigin("*")
@RestController
@RequestMapping("api/payments/momo")
public class MomoPaymentController {

	private static final Logger LOGGER = LoggerFactory.getLogger(MomoPaymentController.class);

	private final RestTemplate restTemplate = new RestTemplate();
	private final ObjectMapper objectMapper = new ObjectMapper();

	@Value("${momo.api.url}")
	private String momoApiUrl;

	@Value("${momo.partner.code}")
	private String partnerCode;

	@Value("${momo.access.key}")
	private String accessKey;

	@Value("${momo.secret.key}")
	private String secretKey;

	@Value("${momo.return.url}")
	private String returnUrl;

	@Value("${momo.notify.url}")
	private String notifyUrl;

	@Value("${momo.request.type}")
	private String requestType;

	@PostMapping("/create")
	public ResponseEntity<?> createPayment(@RequestBody MomoPaymentRequest request) {
		if (request.getAmount() == null || request.getAmount() <= 0) {
			return ResponseEntity.badRequest()
					.body(Collections.singletonMap("message", "Amount must be greater than 0"));
		}

		try {
			String orderId = UUID.randomUUID().toString();
			String requestId = UUID.randomUUID().toString();
			String amount = String.valueOf(request.getAmount());
			String orderInfo = request.getOrderInfo() != null ? request.getOrderInfo() : "MoMo payment";
			String extraData = request.getExtraData() != null ? request.getExtraData() : "";

			String rawSignature = buildRawSignature(accessKey, amount, extraData, requestId, orderId, orderInfo,
					returnUrl, notifyUrl, partnerCode);
			String signature = signHmacSHA256(rawSignature, secretKey);
			LOGGER.info("MoMo raw data: {}", rawSignature);

			Map<String, Object> payload = new HashMap<>();
			payload.put("partnerCode", partnerCode);
			payload.put("accessKey", accessKey);
			payload.put("requestId", requestId);
			payload.put("amount", amount);
			payload.put("orderId", orderId);
			payload.put("orderInfo", orderInfo);
			payload.put("returnUrl", returnUrl);
			payload.put("notifyUrl", notifyUrl);
			payload.put("extraData", extraData);
			payload.put("requestType", requestType);
			payload.put("signature", signature);
			LOGGER.info("MoMo payload: {}", payload);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);

			ResponseEntity<String> momoResponse = restTemplate.postForEntity(momoApiUrl, entity, String.class);
			if (!momoResponse.getStatusCode().is2xxSuccessful() || momoResponse.getBody() == null) {
				LOGGER.error("MoMo API call failed: status={}, body={}", momoResponse.getStatusCode(),
						momoResponse.getBody());
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Collections.singletonMap("message",
						"Hệ thống MoMo đang bận, vui lòng thử lại sau (không nhận được phản hồi)."));
			}

			JsonNode momoBody = objectMapper.readTree(momoResponse.getBody());
			LOGGER.info("MoMo response: {}", momoBody);
			if (!momoBody.hasNonNull("payUrl")) {
				Map<String, Object> errorPayload = new HashMap<>();
				errorPayload.put("message",
						momoBody.hasNonNull("message") ? momoBody.get("message").asText()
								: "Không nhận được liên kết thanh toán MoMo");
				if (momoBody.has("localMessage")) {
					errorPayload.put("localMessage", momoBody.get("localMessage").asText());
				}
				if (momoBody.has("errorCode")) {
					errorPayload.put("errorCode", momoBody.get("errorCode").asText());
				}
				errorPayload.put("rawSignature", rawSignature);
				errorPayload.put("signature", signature);
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorPayload);
			}
			return ResponseEntity.ok(momoBody);
		} catch (Exception ex) {
			LOGGER.error("Cannot create MoMo payment", ex);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
					Collections.singletonMap("message", "Không thể tạo yêu cầu thanh toán MoMo lúc này."));
		}
	}

	@PostMapping("/notify")
	public ResponseEntity<Map<String, Object>> handleNotify(@RequestBody Map<String, Object> payload) {
		LOGGER.info("Received MoMo notify payload: {}", payload);
		Map<String, Object> response = new HashMap<>();
		response.put("message", "Notification received");
		response.put("result", 0);
		return ResponseEntity.ok(response);
	}

	private String buildRawSignature(String accessKey, String amount, String extraData, String requestId, String orderId,
			String orderInfo, String returnUrl, String notifyUrl, String partnerCode) {
		return "partnerCode=" + partnerCode + "&accessKey=" + accessKey + "&requestId=" + requestId + "&amount="
				+ amount + "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&returnUrl=" + returnUrl + "&notifyUrl="
				+ notifyUrl + "&extraData=" + extraData;
	}

	private String signHmacSHA256(String data, String key) throws Exception {
		SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(secretKeySpec);
		byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
		StringBuilder sb = new StringBuilder(2 * hmacData.length);
		for (byte b : hmacData) {
			sb.append(String.format("%02X", b));
		}
		return sb.toString();
	}
}

