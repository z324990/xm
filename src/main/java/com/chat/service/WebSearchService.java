package com.chat.service;

import com.chat.model.SearchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient fastClient;   // 短超时：搜索用
    private final OkHttpClient slowClient;   // 长超时：页面抓取用

    @Value("${search.api.key:}")
    private String serpapiKey;

    private static final Set<String> WEATHER_KW = Set.of("天气", "weather", "温度", "气温", "rain",
            "sunny", "forecast", "meteo", "wind", "humidity", "降水", "湿度", "晴");

    // 简单内存缓存：key=query, value=results, ttl=5分钟
    private static final Map<String, CacheEntry> searchCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 5 * 60 * 1000;

    private static record CacheEntry(List<SearchResult> results, long time) {
        boolean isExpired() { return System.currentTimeMillis() - time > CACHE_TTL_MS; }
    }

    // 共享线程池用于并行页面抓取
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WebSearchService() {
        this.fastClient = new OkHttpClient.Builder()
                .connectTimeout(4, TimeUnit.SECONDS)   // 搜索快速失败
                .readTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();

        this.slowClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)   // 页面抓取稍长
                .readTimeout(6, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }

    public List<SearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank()) return Collections.emptyList();

        // 1) 检查缓存
        CacheEntry cached = searchCache.get(query);
        if (cached != null && !cached.isExpired()) {
            log.info("搜索缓存命中: {}", query);
            return cached.results();
        }

        log.info("搜索: {}", query);

        // 2) 天气查询 → wttr.in（最快路径）
        if (isWeatherQuery(query)) {
            try {
                String weatherData = fetchWeatherData(query);
                if (!weatherData.isEmpty()) {
                    List<SearchResult> result = List.of(
                            new SearchResult("实时天气数据", "https://wttr.in", weatherData));
                    searchCache.put(query, new CacheEntry(result, System.currentTimeMillis()));
                    return result;
                }
            } catch (Exception e) { log.warn("wttr.in 失败: {}", e.getMessage()); }
        }

        // 3) 预判搜索策略：英文新闻类查询，直接用中文搜索避免二次搜索
        String searchQuery = optimizeQuery(query);

        List<SearchResult> results = new ArrayList<>();

        // 4) SerpAPI (如果配置)
        if (serpapiKey != null && !serpapiKey.isBlank()) {
            results = timedSearch("SerpAPI", () -> serpapiSearch(searchQuery, maxResults), 10);
            if (!results.isEmpty()) {
                fetchPageContentsParallel(results, 2);
                searchCache.put(query, new CacheEntry(results, System.currentTimeMillis()));
                return results;
            }
        }

        // 5) Bing 搜索（只搜一次，用优化后的查询词）
        results = timedSearch("Bing", () -> bingSearch(searchQuery, maxResults), 8);
        log.info("Bing 结果: {} 条", results.size());

        // 6) 并行抓取页面内容（仅当 >0 条结果）
        if (!results.isEmpty()) {
            fetchPageContentsParallel(results, Math.min(2, results.size()));
        }

        searchCache.put(query, new CacheEntry(results, System.currentTimeMillis()));
        return results;
    }

    // =================================================================
    //  性能工具：带超时的搜索辅助
    // =================================================================

    /** 带超时的搜索包装 */
    private List<SearchResult> timedSearch(String name, Supplier<List<SearchResult>> fn, long timeoutSec) {
        long deadline = System.currentTimeMillis() + (timeoutSec * 1000);
        try {
            return CompletableFuture.supplyAsync(fn, executor)
                    .orTimeout(timeoutSec, TimeUnit.SECONDS)
                    .exceptionally(e -> {
                        log.warn("{} 超时({}s): {}", name, timeoutSec, e.getMessage());
                        return Collections.emptyList();
                    })
                    .get(timeoutSec + 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("{} 失败: {}", name, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 预判查询词：英文新闻直接转为中文 */
    private String optimizeQuery(String query) {
        String q = query.trim();
        // 英文查询且不是词典类 → 转为中文避免 Bing 返回词典结果
        boolean hasChinese = q.matches(".*[\\u4e00-\\u9fa5].*");
        if (hasChinese) return q;

        // 天气类补充词
        if (WEATHER_KW.stream().anyMatch(kw -> q.toLowerCase().contains(kw))) {
            return q.toLowerCase().contains("weather") ? q : q + " weather";
        }

        // 英文新闻/信息类 → 中文搜索
        String lower = q.toLowerCase();
        if (lower.contains("news") || lower.contains("latest") || lower.contains("today")
                || lower.contains("technology") || lower.contains("update")
                || lower.contains("sports") || lower.contains("business")
                || lower.contains("world") || lower.contains("china")) {
            return translateToChineseQuery(q);
        }

        return q;
    }

    /** 并行抓取页面内容 */
    private void fetchPageContentsParallel(List<SearchResult> results, int limit) {
        if (results.isEmpty() || limit <= 0) return;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            final int idx = i;
            CompletableFuture<Void> f = CompletableFuture.runAsync(() -> {
                try {
                    // 只从结果中提取标题信息，不抓完整页面
                    // 如果 snippet 够长（>80字符），跳过页面抓取
                    String cur = results.get(idx).getSnippet();
                    if (cur != null && cur.length() > 80) return;

                    String content = fetchPageLight(results.get(idx).getUrl());
                    if (!content.isEmpty()) {
                        results.get(idx).setSnippet(
                                (cur != null && !cur.isEmpty() ? cur + "\n" : "") + content);
                    }
                } catch (Exception e) { log.trace("fetch {} 跳过", results.get(idx).getTitle()); }
            }, executor);
            futures.add(f);
        }

        // 等待所有并行任务完成，最多等5秒
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.trace("部分页面抓取未完成: {}", e.getMessage());
        }
    }

    // =================================================================
    //  SerpAPI
    // =================================================================
    private List<SearchResult> serpapiSearch(String query, int maxResults) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://serpapi.com/search?q=" + encoded
                    + "&api_key=" + serpapiKey
                    + "&num=" + Math.min(maxResults, 10)
                    + "&source=web";

            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0").build();

            try (Response resp = fastClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) return Collections.emptyList();
                String json = resp.body() != null ? resp.body().string() : "";
                JsonNode root = objectMapper.readTree(json);
                List<SearchResult> results = new ArrayList<>();

                // organic_results
                JsonNode organic = root.get("organic_results");
                if (organic != null && organic.isArray()) {
                    for (JsonNode item : organic) {
                        if (results.size() >= maxResults) break;
                        String t = item.has("title") ? item.get("title").asText() : "";
                        String l = item.has("link") ? item.get("link").asText() : "";
                        String s = item.has("snippet") ? item.get("snippet").asText() : "";
                        if (!t.isEmpty() && !l.isEmpty()) results.add(new SearchResult(t, l, s));
                    }
                }

                // answer_box (直接答案)
                if (results.isEmpty()) {
                    JsonNode ab = root.get("answer_box");
                    if (ab != null) {
                        String a = ab.has("answer") ? ab.get("answer").asText() : "";
                        if (a.isEmpty() && ab.has("snippet")) a = ab.get("snippet").asText();
                        if (!a.isEmpty()) results.add(new SearchResult(query, "", a));
                    }
                }

                // top_stories
                JsonNode stories = root.get("top_stories");
                if (stories != null && stories.isArray()) {
                    for (JsonNode story : stories) {
                        if (results.size() >= maxResults) break;
                        String t = story.has("title") ? story.get("title").asText() : "";
                        String l = story.has("link") ? story.get("link").asText() : "";
                        if (!t.isEmpty()) results.add(new SearchResult(t, l, ""));
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.warn("SerpAPI 异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // =================================================================
    //  天气
    // =================================================================
    private boolean isWeatherQuery(String query) {
        return WEATHER_KW.stream().anyMatch(kw -> query.toLowerCase().contains(kw));
    }

    private String fetchWeatherData(String query) {
        String city = extractCity(query);
        if (city.isEmpty()) return "";
        try {
            String url = "https://wttr.in/" + URLEncoder.encode(city, "UTF-8")
                    + "?format=%l:+%t+%C+%h+%w&lang=zh";
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "curl/8.0").build();
            try (Response resp = fastClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    String text = resp.body().string().trim();
                    if (!text.isEmpty() && !text.contains("Sorry") && !text.contains("Unknown"))
                        return "天气: " + text;
                }
            }
        } catch (Exception e) { log.warn("wttr.in 失败: {}", e.getMessage()); }
        return "";
    }

    private String extractCity(String query) {
        for (String p : query.split("[,，、\\s]+")) {
            p = p.trim().toLowerCase();
            if (WEATHER_KW.contains(p) || Set.of("today","now","实时","今天","现在").contains(p)) continue;
            return p;
        }
        return "Beijing";
    }

    // =================================================================
    //  Bing 搜索
    // =================================================================
    private List<SearchResult> bingSearch(String query, int maxResults) {
        for (String host : new String[]{"https://cn.bing.com", "https://www.bing.com"}) {
            try {
                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = host + "/search?q=" + encoded + "&count=" + (maxResults + 3);
                Request req = new Request.Builder().url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        .build();
                try (Response resp = fastClient.newCall(req).execute()) {
                    if (!resp.isSuccessful()) continue;
                    String html = resp.body() != null ? resp.body().string() : "";
                    List<SearchResult> results = parseBingHtml(html, maxResults);
                    if (!results.isEmpty()) return results;
                }
            } catch (Exception e) { log.warn("Bing {} 失败: {}", host, e.getMessage()); }
        }
        return Collections.emptyList();
    }

    private List<SearchResult> parseBingHtml(String html, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        int pos = 0;
        while (results.size() < maxResults) {
            int start = html.indexOf("<li class=\"b_algo\"", pos);
            if (start == -1) break;
            int cs = html.indexOf('>', start) + 1;
            int end = html.indexOf("</li>", cs);
            if (cs <= 0 || end < 0) break;
            String item = html.substring(cs, end);
            pos = end + 5;

            // 提取链接
            int hp = item.indexOf("href=\"");
            String url = hp >= 0 ? item.substring(hp + 6, item.indexOf('"', hp + 6)) : null;

            // 提取标题 (第二个 <a>)
            int a1 = item.indexOf("<a ");
            int a2 = a1 >= 0 ? item.indexOf("<a ", a1 + 1) : -1;
            int ta = a2 >= 0 ? a2 : a1;
            String title = "";
            if (ta >= 0) {
                int ts = item.indexOf('>', ta) + 1;
                int te = item.indexOf("</a>", ts);
                if (ts > 0 && te > ts) title = stripTags(item.substring(ts, te)).trim();
            }
            if (title.isEmpty()) continue;

            // 摘要
            String snippet = "";
            int pp = item.indexOf("<p");
            if (pp >= 0) {
                int ss = item.indexOf('>', pp) + 1;
                int se = item.indexOf("</p>", ss);
                if (ss > 0 && se > ss) snippet = stripTags(item.substring(ss, se)).trim();
            }

            results.add(new SearchResult(title, url != null ? url : "", snippet));
        }
        return results;
    }

    // =================================================================
    //  轻量页面内容抓取
    // =================================================================
    private String fetchPageLight(String url) {
        if (url == null || url.isBlank() || url.contains("bing.com")) return "";
        try {
            Request req = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .build();
            try (Response resp = slowClient.newCall(req).execute()) {
                if (!resp.isSuccessful()) return "";
                String html = resp.body() != null ? resp.body().string() : "";
                if (html.isEmpty()) return "";

                // 快速解析：只提取 title + 前几个 <p>
                String title = "";
                var tm = java.util.regex.Pattern.compile("<title>(.*?)</title>",
                        java.util.regex.Pattern.DOTALL).matcher(html);
                if (tm.find()) title = stripTags(tm.group(1)).trim();

                StringBuilder sb = new StringBuilder();
                var pm = java.util.regex.Pattern.compile("<p[^>]*>(.*?)</p>",
                        java.util.regex.Pattern.DOTALL).matcher(html);
                int maxP = 5;
                while (pm.find() && maxP-- > 0) {
                    String t = stripTags(pm.group(1)).trim();
                    if (t.length() > 20) sb.append(t).append("\n");
                }

                String result = (!title.isEmpty() ? title + "\n" : "") + sb;
                return result.length() > 800 ? result.substring(0, 800) + "..." : result.trim();
            }
        } catch (Exception e) { return ""; }
    }

    // =================================================================
    //  格式化
    // =================================================================
    public String formatResultsAsContext(List<SearchResult> results, String originalQuery) {
        if (results == null || results.isEmpty()) return "";

        LocalDateTime now = LocalDateTime.now();
        StringBuilder sb = new StringBuilder();
        sb.append("【系统指令】\n当前真实日期: ")
                .append(now.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")))
                .append("\n\n【用户问题】").append(originalQuery).append("\n\n")
                .append("【搜索结果 (").append(results.size()).append("条)】\n\n");

        int idx = 0;
        for (SearchResult r : results) {
            idx++;
            sb.append("--- 结果").append(idx).append(" ---\n");
            if (!r.getTitle().isEmpty()) sb.append("标题: ").append(r.getTitle()).append("\n");
            if (!r.getUrl().isEmpty()) sb.append("链接: ").append(r.getUrl()).append("\n");
            if (!r.getSnippet().isEmpty()) sb.append("内容: ").append(r.getSnippet()).append("\n");
            sb.append("\n");
        }

        sb.append("【回答要求】\n1. 优先使用搜索结果回答，不要编造数据。\n")
                .append("2. 引用时标注来源序号 [1][2] 等。\n")
                .append("3. 如果搜索结果不足，如实告知。\n4. 用中文回答。\n");
        return sb.toString();
    }

    // =================================================================
    //  工具
    // =================================================================
    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String translateToChineseQuery(String query) {
        String q = query.toLowerCase().trim();
        q = q.replaceAll("latest news", "最新新闻").replaceAll("latest", "最新")
              .replaceAll("news", "新闻").replaceAll("technology", "科技")
              .replaceAll("today", "今天").replaceAll("breaking", "突发")
              .replaceAll("update", "更新").replaceAll("stock market", "股市")
              .replaceAll("election", "选举").replaceAll("president", "总统")
              .replaceAll("china", "中国").replaceAll("weather", "天气")
              .replaceAll("forecast", "预报").replaceAll("sports", "体育")
              .replaceAll("business", "商业").replaceAll("world", "国际");
        if (q.equals(query.toLowerCase().trim())) q = q + " 新闻";
        return q;
    }
}
