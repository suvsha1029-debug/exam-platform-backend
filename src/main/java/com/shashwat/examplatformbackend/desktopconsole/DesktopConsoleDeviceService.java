package com.shashwat.examplatformbackend.desktopconsole;

import com.shashwat.examplatformbackend.desktopconsole.DesktopConsoleDtos.DeviceResponse;
import com.shashwat.examplatformbackend.desktopconsole.DesktopConsoleDtos.EnrollmentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class DesktopConsoleDeviceService {
    private static final Pattern DEVICE_ID = Pattern.compile("^[A-Za-z0-9_.:-]{12,80}$");
    private static final Duration NONCE_TTL = Duration.ofMinutes(3);

    private final DesktopConsoleDeviceRepository repository;
    private final String enrollmentToken;
    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();

    public DesktopConsoleDeviceService(
            DesktopConsoleDeviceRepository repository,
            @Value("${app.desktop-console.enrollment-token:local-dev-console-enrollment-token-change-me}") String enrollmentToken
    ) {
        this.repository = repository;
        this.enrollmentToken = enrollmentToken == null ? "" : enrollmentToken.trim();
    }

    @Transactional
    public DeviceResponse enroll(EnrollmentRequest request, String suppliedToken) {
        if (!constantTimeEquals(enrollmentToken, suppliedToken == null ? "" : suppliedToken.trim()) || enrollmentToken.isBlank()) {
            throw new IllegalArgumentException("Desktop console enrollment token is invalid.");
        }
        String deviceId = clean(request.deviceId());
        String deviceName = clean(request.deviceName());
        String publicKeyBase64 = clean(request.publicKeyBase64());
        if (!DEVICE_ID.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("Desktop console device id is invalid.");
        }
        validatePublicKey(publicKeyBase64);
        DesktopConsoleDevice device = repository.findByDeviceId(deviceId)
                .orElseGet(() -> new DesktopConsoleDevice(deviceId, deviceName, publicKeyBase64));
        device.rotate(deviceName, publicKeyBase64);
        return DeviceResponse.from(repository.save(device));
    }

    @Transactional(readOnly = true)
    public List<DeviceResponse> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream().map(DeviceResponse::from).toList();
    }

    @Transactional
    public DeviceResponse revoke(Long id) {
        DesktopConsoleDevice device = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Desktop console device was not found."));
        device.revoke();
        return DeviceResponse.from(repository.save(device));
    }

    @Transactional
    public boolean verify(String deviceId, String canonical, String signatureBase64, String nonce) {
        if (!DEVICE_ID.matcher(clean(deviceId)).matches()
                || canonical == null
                || clean(signatureBase64).isBlank()
                || clean(nonce).length() < 12) {
            return false;
        }
        String nonceKey = deviceId + ":" + nonce;
        if (seenNonces.containsKey(nonceKey)) {
            return false;
        }
        boolean verified = repository.findByDeviceId(deviceId)
                .filter(DesktopConsoleDevice::isActive)
                .map(device -> verifyWithDevice(device, canonical, signatureBase64))
                .orElse(false);
        if (!verified) {
            return false;
        }
        seenNonces.put(nonceKey, Instant.now().plus(NONCE_TTL));
        if (seenNonces.size() > 10_000) {
            cleanupNonces();
        }
        repository.findByDeviceId(deviceId).ifPresent(DesktopConsoleDevice::touch);
        return true;
    }

    private static boolean verifyWithDevice(DesktopConsoleDevice device, String canonical, String signatureBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(device.getPublicKeyBase64());
            PublicKey publicKey = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception exception) {
            return false;
        }
    }

    private static void validatePublicKey(String publicKeyBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Desktop console public key is invalid.");
        }
    }

    private void cleanupNonces() {
        Instant now = Instant.now();
        seenNonces.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
