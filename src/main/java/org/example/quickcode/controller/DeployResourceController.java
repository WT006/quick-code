package org.example.quickcode.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.quickcode.constant.AppConstant;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

/**
 * 已部署应用的静态资源访问
 * 访问格式：http://localhost:8123/api/deploy/{deployKey}[/{fileName}]
 */
@RestController
@RequestMapping("/deploy")
public class DeployResourceController {

    private static final String DEPLOY_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveDeployResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/deploy/" + deployKey).length());
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            String filePath = DEPLOY_ROOT_DIR + File.separator + deployKey + resourcePath;
            File file = new File(filePath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Type", getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
