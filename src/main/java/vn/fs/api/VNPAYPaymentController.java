package vn.fs.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vn.fs.dto.VNPAYPaymentRequest;

@CrossOrigin("*")
@RestController
@RequestMapping("api/payments/vnpay")
public class VNPAYPaymentController {

	private static final Logger LOGGER = LoggerFactory.getLogger(VNPAYPaymentController.class);

	@Value("${vnpay.tmn.code:}")
	private String tmnCode;

	@Value("${vnpay.hash.secret:}")
	private String hashSecret;

	@Value("${vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
	private String vnpUrl;

	@Value("${vnpay.return.url:}")
	private String returnUrl;

	@Value("${vnpay.ipn.url:}")
	private String ipnUrl;

	@PostMapping("/create")
	public ResponseEntity<?> createPayment(@RequestBody VNPAYPaymentRequest request) {
		try {
			// Validate request
			if (request == null) {
				LOGGER.error("VNPAY payment request is null");
				return ResponseEntity.badRequest()
						.body(Collections.singletonMap("message", "Request không được để trống"));
			}

			LOGGER.info("Received VNPAY payment request: amount={}, orderInfo={}", 
					request.getAmount(), request.getOrderInfo());

			if (request.getAmount() == null || request.getAmount() <= 0) {
				LOGGER.error("Invalid amount: {}", request.getAmount());
				return ResponseEntity.badRequest()
						.body(Collections.singletonMap("message", "Số tiền phải lớn hơn 0"));
			}

			// Validate configuration
			if (tmnCode == null || tmnCode.isEmpty() || hashSecret == null || hashSecret.isEmpty()) {
				LOGGER.error("VNPAY configuration is missing");
				return ResponseEntity.status(500)
						.body(Collections.singletonMap("message", "Cấu hình VNPAY chưa đầy đủ"));
			}

			// Validate return URL
			if (returnUrl == null || returnUrl.isEmpty()) {
				LOGGER.error("VNPAY return URL is not configured");
				return ResponseEntity.status(500)
						.body(Collections.singletonMap("message", "Cấu hình VNPAY return URL chưa đầy đủ"));
			}

			// Create payment URL
			String vnp_TxnRef = String.valueOf(System.currentTimeMillis());
			String vnp_Amount = String.valueOf(request.getAmount() * 100); // VNPAY uses cents
			String vnp_OrderInfo = request.getOrderInfo() != null ? request.getOrderInfo() : "Thanh toan don hang";
			String vnp_OrderType = request.getOrderType() != null ? request.getOrderType() : "other";
			String vnp_Locale = request.getLanguage() != null ? request.getLanguage() : "vn";
			String vnp_IpAddr = "127.0.0.1"; // Will be replaced by VNPAY

			Map<String, String> vnp_Params = new HashMap<>();
			vnp_Params.put("vnp_Version", "2.1.0");
			vnp_Params.put("vnp_Command", "pay");
			vnp_Params.put("vnp_TmnCode", tmnCode);
			vnp_Params.put("vnp_Amount", vnp_Amount);
			vnp_Params.put("vnp_CurrCode", "VND");
			vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
			vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
			vnp_Params.put("vnp_OrderType", vnp_OrderType);
			vnp_Params.put("vnp_Locale", vnp_Locale);
			vnp_Params.put("vnp_ReturnUrl", returnUrl);
			vnp_Params.put("vnp_IpAddr", vnp_IpAddr);
			vnp_Params.put("vnp_CreateDate", java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
					.format(java.time.LocalDateTime.now()));

			if (request.getBankCode() != null && !request.getBankCode().isEmpty()) {
				vnp_Params.put("vnp_BankCode", request.getBankCode());
			}

			// Sort params and create query string
			SortedMap<String, String> sortedParams = new TreeMap<>(vnp_Params);
			StringBuilder queryString = new StringBuilder();
			Iterator<Map.Entry<String, String>> itr = sortedParams.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry<String, String> entry = itr.next();
				try {
					queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString()));
					queryString.append('=');
					queryString.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString()));
				} catch (Exception e) {
					LOGGER.error("Error encoding URL parameter: {}", e.getMessage());
					queryString.append(entry.getKey()).append('=').append(entry.getValue());
				}
				if (itr.hasNext()) {
					queryString.append('&');
				}
			}

			// Create secure hash
			String vnp_SecureHash = hmacSHA512(hashSecret, queryString.toString());
			queryString.append("&vnp_SecureHash=").append(vnp_SecureHash);

			String paymentUrl = vnpUrl + "?" + queryString.toString();

			LOGGER.info("VNPAY payment URL created: {}", paymentUrl);

			Map<String, Object> response = new HashMap<>();
			response.put("code", "00");
			response.put("message", "success");
			response.put("paymentUrl", paymentUrl);
			response.put("txnRef", vnp_TxnRef);

			return ResponseEntity.ok(response);
		} catch (Exception ex) {
			LOGGER.error("Error creating VNPAY payment", ex);
			ex.printStackTrace();
			return ResponseEntity.status(500)
					.body(Collections.singletonMap("message", "Lỗi tạo thanh toán VNPAY: " + ex.getMessage()));
		}
	}

	@GetMapping("/ipn")
	public ResponseEntity<Map<String, Object>> handleIPN(@RequestParam Map<String, String> params) {
		LOGGER.info("Received VNPAY IPN callback: {}", params);
		Map<String, Object> response = new HashMap<>();

		try {
			String vnp_SecureHash = params.get("vnp_SecureHash");
			params.remove("vnp_SecureHash");
			params.remove("vnp_SecureHashType");

			// Sort params
			SortedMap<String, String> sortedParams = new TreeMap<>(params);
			StringBuilder queryString = new StringBuilder();
			Iterator<Map.Entry<String, String>> itr = sortedParams.entrySet().iterator();
			while (itr.hasNext()) {
				Map.Entry<String, String> entry = itr.next();
				queryString.append(entry.getKey()).append('=').append(entry.getValue());
				if (itr.hasNext()) {
					queryString.append('&');
				}
			}

			// Verify hash
			String checkSum = hmacSHA512(hashSecret, queryString.toString());
			if (checkSum.equals(vnp_SecureHash)) {
				String vnp_ResponseCode = params.get("vnp_ResponseCode");
				if ("00".equals(vnp_ResponseCode)) {
					response.put("RspCode", "00");
					response.put("Message", "Confirm Success");
					LOGGER.info("VNPAY payment successful: {}", params.get("vnp_TxnRef"));
				} else {
					response.put("RspCode", vnp_ResponseCode);
					response.put("Message", "Payment failed");
					LOGGER.warn("VNPAY payment failed: {}", params);
				}
			} else {
				response.put("RspCode", "97");
				response.put("Message", "Checksum failed");
				LOGGER.error("VNPAY IPN checksum verification failed");
			}
		} catch (Exception ex) {
			LOGGER.error("Error processing VNPAY IPN", ex);
			response.put("RspCode", "99");
			response.put("Message", "Unknown error");
		}

		return ResponseEntity.ok(response);
	}

	@GetMapping("/return")
	public ResponseEntity<?> handleReturn(@RequestParam Map<String, String> params) {
		LOGGER.info("Received VNPAY return callback: {}", params);
		return ResponseEntity.ok(params);
	}

	private String hmacSHA512(String key, String data) {
		try {
			Mac hmacSHA512 = Mac.getInstance("HmacSHA512");
			SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
			hmacSHA512.init(secretKey);
			byte[] digest = hmacSHA512.doFinal(data.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : digest) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (Exception ex) {
			LOGGER.error("Error generating HMAC SHA512", ex);
			return "";
		}
	}
}

