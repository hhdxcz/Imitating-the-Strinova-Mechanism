package com.hhdxcz.strinova.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhdxcz.strinova.StrinovaMod;
import com.hhdxcz.strinova.client.hud.StrinovaHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公告中心（客户端）。
 *
 * <p>流程：玩家进入世界（含单人/联机）后，后台线程检测联网并拉取在线公告；
 * 联网成功且有未读公告（首次进入，或模组/在线公告新增了 id）时，自动弹出
 * {@link StrinovaAnnouncementScreen}。已读 id 存在 {@link StrinovaHudConfig} 里，
 * 所以同一条公告只会弹一次；远程内容更新（新增 id）会再次弹出。</p>
 *
 * <p>内容来源：优先在线 JSON（地址可通过 config/strinova-client.json 的
 * {@code announcementUrls} 覆盖，默认 jsDelivr + GitHub raw），拉取失败但网络可达时
 * 回退到模组内置的 {@code assets/strinova/announcements.json}。</p>
 */
public final class StrinovaAnnouncements {

    /** 模组内置公告（随 jar 打包，离线/远程不可达时的兜底内容）。 */
    public static final String BUNDLED_RESOURCE = "/assets/strinova/announcements.json";
    /** 默认在线公告地址（中文网络环境优先 jsDelivr，其次 GitHub raw）。 */
    public static final String[] DEFAULT_URLS = {
            "https://cdn.jsdelivr.net/gh/hhdxcz/Imitating-the-Strinova-Mechanism@main/announcements.json",
            "https://raw.githubusercontent.com/hhdxcz/Imitating-the-Strinova-Mechanism/main/announcements.json"
    };
    /** 联网探测地址（任意一个返回 HTTP 响应即视为设备在线）。 */
    private static final String[] ONLINE_PROBES = {
            "https://www.baidu.com",
            "https://www.bing.com",
            "https://www.mozilla.org"
    };

    /** 远端内容大小上限（防御异常数据）。 */
    private static final int MAX_BYTES = 512 * 1024;
    /** 进入世界后延迟多少 tick 再弹（避免加载界面/传送中弹出）。 */
    private static final int JOIN_DELAY_TICKS = 60;
    /** 最多等多久（tick），窗口一直被其它界面占用就放弃本次自动弹出。 */
    private static final int GIVE_UP_TICKS = 1200;
    /** 单次网络请求超时（毫秒）。 */
    private static final int HTTP_TIMEOUT_MS = 6000;
    /** 联网探测超时（毫秒）。 */
    private static final int PROBE_TIMEOUT_MS = 2500;

    /**
     * 一条公告。
     * category 约定：update（更新公告）/ guide（游玩说明）/ version（版本说明）/
     * event（活动公告）/ notice（系统公告），其它任意字符串会显示为「其他」下的分类名。
     */
    public static final class Entry {
        public final String id;
        public final String category;
        public final String title;
        public final String version;
        public final String date;
        public final String link;
        public final List<String> body;

        private Entry(String id, String category, String title, String version,
                      String date, String link, List<String> body) {
            this.id = id;
            this.category = category;
            this.title = title;
            this.version = version;
            this.date = date;
            this.link = link;
            this.body = body;
        }

        /** 右上角灰色元信息：「v1.3.24 · 2026-11-21」。 */
        public String metaLine() {
            StringBuilder builder = new StringBuilder();
            if (version != null && !version.isBlank()) {
                builder.append(version.startsWith("v") ? version : "v" + version);
            }
            if (date != null && !date.isBlank()) {
                if (builder.length() > 0) {
                    builder.append(" · ");
                }
                builder.append(date);
            }
            return builder.toString();
        }

        /** 用于按时间倒序：缺日期排最后。 */
        private String dateKey() {
            return date == null || date.isBlank() ? "0000-00-00" : date;
        }
    }

    private static List<Entry> bundledEntries;
    private static volatile List<Entry> remoteEntries;
    private static volatile boolean fetching;
    private static volatile boolean onlineKnown;
    private static volatile boolean online;

    /** 世界会话：-1 = 不在世界。 */
    private static int ticksSinceJoin = -1;
    private static boolean autoPending;

    private StrinovaAnnouncements() {
    }

    // ==================== 世界生命周期 ====================

    /** 进入世界（单人/联机）时调用：开始联网检测并按需自动弹出。 */
    public static void onWorldJoin() {
        ticksSinceJoin = 0;
        autoPending = StrinovaHudConfig.get().announcementEnabled;
        // 每次进世界重新检测一次联网，避免上次离线/在线状态过期
        onlineKnown = false;
        online = false;
        remoteEntries = null;
        refreshAsync();
    }

    /** 断开连接时调用。 */
    public static void reset() {
        ticksSinceJoin = -1;
        autoPending = false;
        onlineKnown = false;
        online = false;
        remoteEntries = null;
    }

    /** 客户端每 tick：等联网结果 + 玩家进世界 + 当前没有其它界面，再弹出未读公告。 */
    public static void tick(Minecraft client) {
        if (ticksSinceJoin < 0) {
            return;
        }
        ticksSinceJoin++;
        if (!autoPending) {
            return;
        }
        if (!StrinovaHudConfig.get().announcementEnabled) {
            autoPending = false;
            return;
        }
        if (ticksSinceJoin > GIVE_UP_TICKS) {
            // 一直有界面占着（比如开局选角），这次就不弹了，避免打断对局
            autoPending = false;
            return;
        }
        if (!onlineKnown) {
            return;
        }
        if (!online) {
            autoPending = false;
            StrinovaMod.LOGGER.info("[Strinova] 公告：设备未联网，跳过进入世界公告弹窗");
            return;
        }
        if (ticksSinceJoin < JOIN_DELAY_TICKS) {
            return;
        }
        if (client == null || client.player == null || client.screen != null) {
            return;
        }
        if (unseenEntries().isEmpty()) {
            autoPending = false;
            return;
        }
        autoPending = false;
        client.setScreen(new StrinovaAnnouncementScreen(null, true));
    }

    // ==================== 内容 ====================

    /** 当前可用公告（在线优先，在线内容里没出现的 id 用内置内容补齐），按日期倒序。 */
    public static List<Entry> currentEntries() {
        List<Entry> remote = remoteEntries;
        List<Entry> bundled = bundled();
        List<Entry> merged = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (remote != null) {
            for (Entry entry : remote) {
                if (ids.add(entry.id)) {
                    merged.add(entry);
                }
            }
        }
        for (Entry entry : bundled) {
            if (ids.add(entry.id)) {
                merged.add(entry);
            }
        }
        merged.sort(Comparator.comparing(Entry::dateKey).reversed());
        return merged;
    }

    /** 还没读过的公告。 */
    public static List<Entry> unseenEntries() {
        StrinovaHudConfig config = StrinovaHudConfig.get();
        List<Entry> result = new ArrayList<>();
        for (Entry entry : currentEntries()) {
            if (!config.isAnnouncementSeen(entry.id)) {
                result.add(entry);
            }
        }
        return result;
    }

    /** 把当前所有公告标记为已读（公告界面关闭时调用）。 */
    public static void acknowledgeAll() {
        List<String> ids = new ArrayList<>();
        for (Entry entry : currentEntries()) {
            ids.add(entry.id);
        }
        StrinovaHudConfig.get().markAnnouncementsSeen(ids);
    }

    /** 清除已读记录（调试命令）。 */
    public static void clearSeen() {
        StrinovaHudConfig.get().clearAnnouncementSeen();
    }

    /** 打开公告界面（设置页/命令手动查看时不消耗自动弹出）。 */
    public static void openScreen(Screen parent, boolean autoShown) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        if (remoteEntries == null && !fetching) {
            refreshAsync();
        }
        minecraft.setScreen(new StrinovaAnnouncementScreen(parent, autoShown));
    }

    /** 公告来源文案键（显示在界面底部）。 */
    public static String sourceLabelKey() {
        if (!onlineKnown) {
            return "config.strinova.announcement.source.checking";
        }
        if (online) {
            return remoteEntries != null && !remoteEntries.isEmpty()
                    ? "config.strinova.announcement.source.online"
                    : "config.strinova.announcement.source.bundled";
        }
        return "config.strinova.announcement.source.offline";
    }

    /** 是否已知道联网状态（用于设置页显示）。 */
    public static boolean isOnlineKnown() {
        return onlineKnown;
    }

    // ==================== 联网拉取 ====================

    /** 后台联网拉取在线公告；正在拉取时直接返回。 */
    public static synchronized void refreshAsync() {
        if (fetching) {
            return;
        }
        fetching = true;
        Thread thread = new Thread(StrinovaAnnouncements::fetchLoop, "strinova-announcements");
        thread.setDaemon(true);
        try {
            thread.start();
        } catch (Throwable throwable) {
            fetching = false;
            StrinovaMod.LOGGER.warn("[Strinova] 公告线程启动失败", throwable);
        }
    }

    private static void fetchLoop() {
        // 先探测联网（快，决定要不要自动弹窗），远程内容随后补上，不拖延弹窗
        boolean isOnline;
        try {
            isOnline = probeOnline();
        } catch (Throwable throwable) {
            isOnline = false;
        }
        online = isOnline;
        onlineKnown = true;

        List<Entry> fetched = null;
        try {
            if (isOnline) {
                for (String url : configuredUrls()) {
                    String json = httpGet(url, HTTP_TIMEOUT_MS);
                    if (json == null) {
                        continue;
                    }
                    List<Entry> parsed = parse(json);
                    if (parsed != null && !parsed.isEmpty()) {
                        fetched = parsed;
                        StrinovaMod.LOGGER.info("[Strinova] 公告：在线内容拉取成功（{} 条，来自 {}）", parsed.size(), url);
                        break;
                    }
                }
            }
        } catch (Throwable throwable) {
            StrinovaMod.LOGGER.warn("[Strinova] 公告拉取异常", throwable);
        }
        applyFetched(fetched, isOnline);
    }

    private static void applyFetched(List<Entry> fetched, boolean isOnline) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable apply = () -> {
            remoteEntries = fetched;
            online = isOnline || fetched != null;
            onlineKnown = true;
            fetching = false;
            if (fetched != null && !fetched.isEmpty()) {
                StrinovaMod.LOGGER.info("[Strinova] 公告：在线 {} 条，未读 {} 条",
                        fetched.size(), unseenCountSafe());
            } else {
                StrinovaMod.LOGGER.info("[Strinova] 公告：未获取到在线内容（online={}），使用内置内容", isOnline);
            }
        };
        if (minecraft == null) {
            apply.run();
            return;
        }
        try {
            minecraft.execute(apply);
        } catch (Throwable throwable) {
            // 游戏关闭中：直接在后台线程落状态即可
            apply.run();
        }
    }

    /** 在主线程安全地统计未读数（只在 apply 里拼日志时用）。 */
    private static int unseenCountSafe() {
        try {
            return unseenEntries().size();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static List<String> configuredUrls() {
        List<String> urls = StrinovaHudConfig.get().announcementUrlList();
        if (urls.isEmpty()) {
            for (String url : DEFAULT_URLS) {
                urls.add(url);
            }
        }
        return urls;
    }

    /** 简单 GET；2xx 返回正文，其它返回 null。 */
    private static String httpGet(String url, int timeoutMs) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Strinova-Minecraft-Mod/announcements");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            try (InputStream input = connection.getInputStream()) {
                return readAll(input);
            }
        } catch (Throwable throwable) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** 联网探测：任意探测地址返回 HTTP 响应即视为设备在线。 */
    private static boolean probeOnline() {
        for (String probe : ONLINE_PROBES) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(probe).openConnection();
                connection.setConnectTimeout(PROBE_TIMEOUT_MS);
                connection.setReadTimeout(PROBE_TIMEOUT_MS);
                connection.setRequestMethod("HEAD");
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Strinova-Minecraft-Mod/announcements");
                int code = connection.getResponseCode();
                if (code > 0 && code < 500) {
                    return true;
                }
            } catch (Throwable ignored) {
                // 换下一个探测地址
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return false;
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BYTES) {
                throw new IllegalStateException("announcement payload too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    // ==================== 解析 ====================

    /** 内置公告（惰性加载；读取失败返回空列表，不抛异常）。 */
    public static List<Entry> bundled() {
        if (bundledEntries == null) {
            bundledEntries = loadBundled();
        }
        return bundledEntries;
    }

    private static List<Entry> loadBundled() {
        try (InputStream input = StrinovaAnnouncements.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (input == null) {
                StrinovaMod.LOGGER.warn("[Strinova] 公告：内置资源缺失 {}", BUNDLED_RESOURCE);
                return List.of();
            }
            List<Entry> parsed = parse(readAll(input));
            return parsed == null ? List.of() : parsed;
        } catch (Throwable throwable) {
            StrinovaMod.LOGGER.warn("[Strinova] 公告：内置资源解析失败", throwable);
            return List.of();
        }
    }

    /** 解析公告 JSON：兼容「数组根」与「{announcements:[...]}」两种结构。 */
    private static List<Entry> parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else if (root.isJsonObject()) {
                JsonObject object = root.getAsJsonObject();
                JsonElement element = object.has("announcements")
                        ? object.get("announcements")
                        : object.get("entries");
                if (element == null || !element.isJsonArray()) {
                    return null;
                }
                array = element.getAsJsonArray();
            } else {
                return null;
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                Entry entry = parseEntry(element.getAsJsonObject());
                if (entry != null) {
                    entries.add(entry);
                }
            }
            return entries;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Entry parseEntry(JsonObject object) {
        String title = string(object, "title");
        if (title.isBlank()) {
            return null;
        }
        String category = string(object, "category");
        if (category.isBlank()) {
            category = string(object, "type");
        }
        if (category.isBlank()) {
            category = "notice";
        }
        category = category.toLowerCase(java.util.Locale.ROOT);
        String version = string(object, "version");
        String date = string(object, "date");
        String link = string(object, "link");
        String id = string(object, "id");
        if (id.isBlank()) {
            id = Integer.toHexString((category + "|" + version + "|" + title).hashCode());
        }
        List<String> body = new ArrayList<>();
        JsonElement bodyElement = object.has("body") ? object.get("body") : object.get("content");
        if (bodyElement != null) {
            if (bodyElement.isJsonArray()) {
                for (JsonElement line : bodyElement.getAsJsonArray()) {
                    if (line != null && line.isJsonPrimitive()) {
                        body.add(line.getAsString());
                    }
                }
            } else if (bodyElement.isJsonPrimitive()) {
                for (String line : bodyElement.getAsString().split("\\r?\\n")) {
                    body.add(line);
                }
            }
        }
        return new Entry(id, category, title.trim(), version.trim(), date.trim(), link.trim(), body);
    }

    private static String string(JsonObject object, String key) {
        if (object == null || !object.has(key)) {
            return "";
        }
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return "";
        }
        try {
            return element.getAsString();
        } catch (Throwable ignored) {
            return "";
        }
    }
}
