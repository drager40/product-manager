package com.bugs.productmanager.controller;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Duration;
import java.util.*;

@Controller
@RequestMapping("/monitor")
public class MonitorController {

    private final CacheManager cacheManager;
    private final DataSource dataSource;

    public MonitorController(CacheManager cacheManager, DataSource dataSource) {
        this.cacheManager = cacheManager;
        this.dataSource = dataSource;
    }

    @GetMapping
    public String monitor(Authentication auth, Model model) {
        // ADMIN만 접근 가능
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) return "redirect:/";

        // 캐시 통계
        List<Map<String, Object>> cacheList = new ArrayList<>();
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", name);
                info.put("size", nativeCache.estimatedSize());
                info.put("hitCount", stats.hitCount());
                info.put("missCount", stats.missCount());
                info.put("hitRate", stats.requestCount() > 0
                        ? String.format("%.1f%%", stats.hitRate() * 100) : "0.0%");
                info.put("evictionCount", stats.evictionCount());
                info.put("requestCount", stats.requestCount());
                cacheList.add(info);
            }
        }
        model.addAttribute("cacheList", cacheList);

        // 전체 캐시 합계
        long totalHits = cacheList.stream().mapToLong(c -> (long) c.get("hitCount")).sum();
        long totalMisses = cacheList.stream().mapToLong(c -> (long) c.get("missCount")).sum();
        long totalRequests = totalHits + totalMisses;
        String totalHitRate = totalRequests > 0
                ? String.format("%.1f%%", (double) totalHits / totalRequests * 100) : "0.0%";
        model.addAttribute("totalHits", totalHits);
        model.addAttribute("totalMisses", totalMisses);
        model.addAttribute("totalRequests", totalRequests);
        model.addAttribute("totalHitRate", totalHitRate);

        // JVM 메모리
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memBean.getHeapMemoryUsage().getUsed();
        long heapMax = memBean.getHeapMemoryUsage().getMax();
        long nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed();
        model.addAttribute("heapUsed", formatBytes(heapUsed));
        model.addAttribute("heapMax", formatBytes(heapMax));
        model.addAttribute("heapPercent", heapMax > 0 ? (int) (heapUsed * 100 / heapMax) : 0);
        model.addAttribute("nonHeapUsed", formatBytes(nonHeapUsed));

        // 서버 가동 시간
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtimeBean.getUptime();
        Duration uptime = Duration.ofMillis(uptimeMs);
        String uptimeStr = String.format("%d일 %d시간 %d분",
                uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart());
        model.addAttribute("uptime", uptimeStr);

        // 스레드 수
        model.addAttribute("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());

        // Java 버전
        model.addAttribute("javaVersion", System.getProperty("java.version"));
        model.addAttribute("osName", System.getProperty("os.name") + " " + System.getProperty("os.arch"));

        // DB 정보
        model.addAllAttributes(getDbInfo());

        return "monitor";
    }

    @GetMapping("/api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> monitorApi(Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) return ResponseEntity.status(403).build();

        Map<String, Object> data = new LinkedHashMap<>();

        // 캐시 통계
        List<Map<String, Object>> cacheList = new ArrayList<>();
        for (String name : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(name);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();
                CacheStats stats = nativeCache.stats();
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", name);
                info.put("size", nativeCache.estimatedSize());
                info.put("hitCount", stats.hitCount());
                info.put("missCount", stats.missCount());
                info.put("hitRate", stats.requestCount() > 0
                        ? String.format("%.1f%%", stats.hitRate() * 100) : "0.0%");
                info.put("evictionCount", stats.evictionCount());
                info.put("requestCount", stats.requestCount());
                cacheList.add(info);
            }
        }
        data.put("cacheList", cacheList);

        long totalHits = cacheList.stream().mapToLong(c -> (long) c.get("hitCount")).sum();
        long totalMisses = cacheList.stream().mapToLong(c -> (long) c.get("missCount")).sum();
        long totalRequests = totalHits + totalMisses;
        data.put("totalHits", totalHits);
        data.put("totalMisses", totalMisses);
        data.put("totalRequests", totalRequests);
        data.put("totalHitRate", totalRequests > 0
                ? String.format("%.1f%%", (double) totalHits / totalRequests * 100) : "0.0%");

        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memBean.getHeapMemoryUsage().getUsed();
        long heapMax = memBean.getHeapMemoryUsage().getMax();
        data.put("heapUsed", formatBytes(heapUsed));
        data.put("heapMax", formatBytes(heapMax));
        data.put("heapPercent", heapMax > 0 ? (int) (heapUsed * 100 / heapMax) : 0);
        data.put("nonHeapUsed", formatBytes(memBean.getNonHeapMemoryUsage().getUsed()));

        Duration uptime = Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime());
        data.put("uptime", String.format("%d일 %d시간 %d분",
                uptime.toDays(), uptime.toHoursPart(), uptime.toMinutesPart()));
        data.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());

        // DB 정보
        data.putAll(getDbInfo());

        return ResponseEntity.ok(data);
    }

    private Map<String, Object> getDbInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            // HikariCP 커넥션 풀 정보
            if (dataSource instanceof HikariDataSource hikari) {
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    info.put("dbActiveConns", pool.getActiveConnections());
                    info.put("dbIdleConns", pool.getIdleConnections());
                    info.put("dbTotalConns", pool.getTotalConnections());
                    info.put("dbWaitingThreads", pool.getThreadsAwaitingConnection());
                }
                info.put("dbPoolMax", hikari.getMaximumPoolSize());
                info.put("dbPoolMin", hikari.getMinimumIdle());
                info.put("dbConnTimeout", hikari.getConnectionTimeout() / 1000 + "초");
                info.put("dbMaxLifetime", hikari.getMaxLifetime() / 1000 / 60 + "분");
                info.put("dbPoolName", hikari.getPoolName());
            }

            // DB 메타 정보
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                info.put("dbProduct", meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion());
                info.put("dbDriver", meta.getDriverName() + " " + meta.getDriverVersion());
                info.put("dbUrl", meta.getURL());
                info.put("dbCatalog", conn.getCatalog());
            }
        } catch (Exception e) {
            info.put("dbError", e.getMessage());
        }
        return info;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
