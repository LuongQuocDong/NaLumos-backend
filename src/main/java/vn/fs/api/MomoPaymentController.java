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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import vn.fs.dto.MomoPaymentRequest;

// Disabled - Đã chuyển sang VNPAY
// @CrossOrigin("*")
// @RestController
// @RequestMapping("api/payments/momo")
public class MomoPaymentController {

	private static final Logger LOGGER = LoggerFactory.getLogger(MomoPaymentController.class);

	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();
	
	public MomoPaymentController() {
		this.restTemplate = new RestTemplate();
		// Set timeout to prevent hanging requests
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
				new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10000); // 10 seconds
		factory.setReadTimeout(30000); // 30 seconds
		this.restTemplate.setRequestFactory(factory);
	}

	@Value("${momo.api.url:https://test-payment.momo.vn/v2/gateway/api/create}")
	private String momoApiUrl;

	@Value("${momo.partner.code:MOMO}")
	private String partnerCode;

	@Value("${momo.access.key:F8BBA842ECF85}")
	private String accessKey;

	@Value("${momo.secret.key:K951B6PE1waDMi640xX08PD3vg6EKvLz}")
	private String secretKey;

	@Value("${momo.return.url:https://nalumosshop-web-production.up.railway.app/checkout}")
	private String returnUrl;

	@Value("${momo.notify.url:https://nalumos-backend-production.up.railway.app/api/payments/momo/notify}")
	private String notifyUrl;

	@Value("${momo.request.type:captureMoMoWallet}")
	private String requestType;

	@PostMapping("/create")
	public ResponseEntity<?> createPayment(@RequestBody(required = false) MomoPaymentRequest request) {
		try {
			LOGGER.info("Received MoMo payment request");
			
			// Validate request is not null
			if (request == null) {
				LOGGER.error("MoMo payment request is null");
				return ResponseEntity.badRequest()
						.body(Collections.singletonMap("message", "Request không được để trống"));
			}
			
			LOGGER.info("MoMo payment request: amount={}, orderInfo={}", 
					request.getAmount(), request.getOrderInfo());
			
			// Validate request
			if (request.getAmount() == null || request.getAmount() <= 0) {
				LOGGER.error("Invalid amount: {}", request.getAmount());
				return ResponseEntity.badRequest()
						.body(Collections.singletonMap("message", "Số tiền phải lớn hơn 0"));
			}

			// Validate configuration
			if (partnerCode == null || partnerCode.isEmpty()) {
				LOGGER.error("MoMo partnerCode is not configured");
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(Collections.singletonMap("message", "Cấu hình MoMo chưa đầy đủ"));
			}
			
			if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
				LOGGER.error("MoMo accessKey or secretKey is not configured");
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(Collections.singletonMap("message", "Cấu hình MoMo chưa đầy đủ"));
			}
			String orderId = UUID.randomUUID().toString();
			String requestId = UUID.randomUUID().toString();
			String amount = String.valueOf(request.getAmount());
			String orderInfo = request.getOrderInfo() != null ? request.getOrderInfo() : "Thanh toán MoMo";
			String extraData = request.getExtraData() != null ? request.getExtraData() : "";

			String rawSignature = buildRawSignature(accessKey, amount, extraData, requestId, orderId, orderInfo,
					returnUrl, notifyUrl, partnerCode, requestType);
			String signature;
			try {
				signature = signHmacSHA256(rawSignature, secretKey);
			} catch (Exception sigEx) {
				LOGGER.error("Failed to generate signature", sigEx);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
						.body(Collections.singletonMap("message", "Lỗi tạo chữ ký: " + sigEx.getMessage()));
			}
			LOGGER.info("MoMo raw signature: {}", rawSignature);
			LOGGER.info("MoMo signature: {}", signature);
			LOGGER.info("MoMo API URL: {}", momoApiUrl);

			Map<String, Object> payload = new HashMap<>();
			payload.put("partnerCode", partnerCode);
			payload.put("accessKey", accessKey);
			payload.put("requestId", requestId);
			payload.put("amount", amount);
			payload.put("orderId", orderId);
			payload.put("orderInfo", orderInfo);
			payload.put("redirectUrl", returnUrl);
			payload.put("ipnUrl", notifyUrl);
			payload.put("extraData", extraData);
			payload.put("requestType", requestType);
			payload.put("signature", signature);
			payload.put("lang", "vi");
			LOGGER.info("MoMo request payload: {}", payload);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
			String jsonPayload = objectMapper.writeValueAsString(payload);
			LOGGER.info("MoMo request JSON: {}", jsonPayload);
			HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

			LOGGER.info("Sending request to MoMo API: {}", momoApiUrl);
			ResponseEntity<String> momoResponse = restTemplate.postForEntity(momoApiUrl, entity, String.class);
			LOGGER.info("MoMo API response status: {}", momoResponse.getStatusCode());
			LOGGER.info("MoMo API response body: {}", momoResponse.getBody());
			
			if (!momoResponse.getStatusCode().is2xxSuccessful() || momoResponse.getBody() == null) {
				LOGGER.error("MoMo API call failed: status={}, body={}", momoResponse.getStatusCode(),
						momoResponse.getBody());
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Collections.singletonMap("message",
						"Hệ thống MoMo đang bận, vui lòng thử lại sau (không nhận được phản hồi)."));
			}

			JsonNode momoBody = objectMapper.readTree(momoResponse.getBody());
			LOGGER.info("MoMo response parsed: {}", momoBody);
			
			if (!momoBody.hasNonNull("payUrl")) {
				Map<String, Object> errorPayload = new HashMap<>();
				String errorMessage = momoBody.hasNonNull("message") ? momoBody.get("message").asText()
						: "Không nhận được liên kết thanh toán MoMo";
				errorPayload.put("message", errorMessage);
				LOGGER.error("MoMo payment creation failed: {}", errorMessage);
				
				if (momoBody.has("localMessage")) {
					errorPayload.put("localMessage", momoBody.get("localMessage").asText());
					LOGGER.error("MoMo localMessage: {}", momoBody.get("localMessage").asText());
				}
				if (momoBody.has("errorCode")) {
					errorPayload.put("errorCode", momoBody.get("errorCode").asText());
					LOGGER.error("MoMo errorCode: {}", momoBody.get("errorCode").asText());
				}
				if (momoBody.has("resultCode")) {
					errorPayload.put("resultCode", momoBody.get("resultCode").asText());
					LOGGER.error("MoMo resultCode: {}", momoBody.get("resultCode").asText());
				}
				errorPayload.put("rawSignature", rawSignature);
				errorPayload.put("signature", signature);
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorPayload);
			}
			
			LOGGER.info("MoMo payment URL created successfully: {}", momoBody.get("payUrl").asText());
			return ResponseEntity.ok(momoBody);
		} catch (HttpStatusCodeException httpEx) {
			LOGGER.error("MoMo API HTTP error: status={}, body={}", httpEx.getStatusCode(),
					httpEx.getResponseBodyAsString(), httpEx);
			try {
				JsonNode errorBody = objectMapper.readTree(httpEx.getResponseBodyAsString());
				LOGGER.error("MoMo error response: {}", errorBody);
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody);
			} catch (Exception parseEx) {
				LOGGER.error("Failed to parse MoMo error response", parseEx);
				Map<String, Object> errorResponse = new HashMap<>();
				errorResponse.put("message", "MoMo trả về lỗi: " + httpEx.getStatusText());
				errorResponse.put("statusCode", httpEx.getStatusCode().value());
				errorResponse.put("responseBody", httpEx.getResponseBodyAsString());
				return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorResponse);
			}
		} catch (Exception ex) {
			LOGGER.error("Unexpected error creating MoMo payment", ex);
			ex.printStackTrace();
			Map<String, Object> errorResponse = new HashMap<>();
			errorResponse.put("message", "Không thể tạo yêu cầu thanh toán MoMo: " + ex.getMessage());
			errorResponse.put("error", ex.getClass().getSimpleName());
			if (ex.getCause() != null) {
				errorResponse.put("cause", ex.getCause().getMessage());
			}
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
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
			String orderInfo, String returnUrl, String notifyUrl, String partnerCode, String requestType) {
		return "accessKey=" + accessKey + "&amount=" + amount + "&extraData=" + extraData + "&ipnUrl=" + notifyUrl
				+ "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + partnerCode + "&redirectUrl="
				+ returnUrl + "&requestId=" + requestId + "&requestType=" + requestType;
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

