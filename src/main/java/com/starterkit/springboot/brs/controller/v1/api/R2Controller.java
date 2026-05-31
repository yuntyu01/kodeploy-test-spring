package com.starterkit.springboot.brs.controller.v1.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * R2(S3 호환) 오브젝트 스토리지 동작 검증용 — KoDeploy storage 토글 ON 배포 시
 * 주입되는 S3_ 계열(및 AWS_ 별칭) 환경변수만으로 실제 업로드/조회가 되는지 확인.
 *
 * Dailo의 S3Config가 R2로 갈 때 필요한 설정(endpointOverride + path-style + region "auto")을
 * 그대로 미러링한다 — 여기서 통과하면 Dailo 변경도 동일하게 먹힌다는 리허설.
 */
@RestController
@RequestMapping("/api/v1/r2")
public class R2Controller {

    // KoDeploy가 r2-secret으로 주입하는 키들 (S3_* 규약).
    private String endpoint()   { return System.getenv("S3_ENDPOINT"); }
    private String bucket()     { return System.getenv("S3_BUCKET"); }
    private String accessKey()  { return System.getenv("S3_ACCESS_KEY"); }
    private String secretKey()  { return System.getenv("S3_SECRET_KEY"); }
    private String publicBase() { return System.getenv("S3_PUBLIC_BASE_URL"); }

    private volatile S3Client client;

    private boolean configured() {
        return notEmpty(endpoint()) && notEmpty(bucket())
                && notEmpty(accessKey()) && notEmpty(secretKey());
    }

    // 첫 사용 시 한 번만 빌드 (storage OFF여도 앱은 정상 부팅 — 호출 때만 503).
    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .endpointOverride(URI.create(endpoint()))   // R2 엔드포인트
                            .region(Region.of("auto"))                  // R2 region = auto
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(accessKey(), secretKey())))
                            // 커스텀 엔드포인트엔 path-style이 가장 안전 (가상호스트 서브도메인 회피)
                            .serviceConfiguration(S3Configuration.builder()
                                    .pathStyleAccessEnabled(true).build())
                            .httpClient(UrlConnectionHttpClient.create())
                            .build();
                }
            }
        }
        return client;
    }

    /** 주입 상태 점검 — storage 토글이 켜졌고 env가 들어왔는지. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("configured", configured());
        m.put("endpoint", endpoint());
        m.put("bucket", bucket());
        m.put("publicBase", publicBase());
        return m;
    }

    /** 파일 업로드 → R2에 PUT → 공개 URL 반환. */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (!configured()) {
            return ResponseEntity.status(503)
                    .body(Collections.singletonMap("error", "R2 미설정 — storage 토글 ON으로 배포했는지 확인"));
        }
        String key = "uploads/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        client().putObject(
                PutObjectRequest.builder()
                        .bucket(bucket())
                        .key(key)
                        .contentType(file.getContentType())
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("url", (publicBase() == null ? "" : publicBase()) + "/" + key);  // 공개 URL로 바로 읽기 가능
        return ResponseEntity.ok(m);
    }

    /** 버킷의 객체 목록 (최대 100개). */
    @GetMapping("/list")
    public ResponseEntity<?> list() {
        if (!configured()) {
            return ResponseEntity.status(503)
                    .body(Collections.singletonMap("error", "R2 미설정"));
        }
        ListObjectsV2Response resp = client().listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket()).maxKeys(100).build());
        List<String> keys = resp.contents().stream().map(S3Object::key).collect(Collectors.toList());
        return ResponseEntity.ok(Collections.singletonMap("keys", keys));
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
