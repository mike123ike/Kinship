package com.pkg.civicfix;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalNewsRepository {

    private static final String ENDPOINT = "https://newsdata.io/api/1/latest";

    // cache duration to prevent burning api credits on tab switches
    private static final long CACHE_DURATION_MS = 20L * 60L * 1000L;

    // civic keywords required in title or description
    private static final String[] CIVIC_KEYWORDS = {

            // roads / transportation
            "road",
            "roads",
            "street",
            "streets",
            "traffic",
            "closure",
            "closures",
            "closed",
            "construction",
            "roadwork",
            "infrastructure",
            "bridge",
            "sidewalk",
            "transit",

            // traffic incidents
            "crash",
            "collision",
            "accident",

            // public safety
            "police",
            "public safety",
            "emergency",
            "arrest",
            "arrests",
            "shooting",
            "suspect",
            "fire",
            "firefighters",

            // utilities
            "water",
            "water main",
            "sewer",
            "wastewater",
            "utility",
            "utilities",
            "outage",
            "power outage",
            "power",
            "electric",

            // environment / weather
            "environment",
            "environmental",
            "pollution",
            "air quality",
            "flood",
            "flooding",
            "storm",
            "weather",
            "drought",
            "conservation",

            // public spaces
            "park",
            "parks",
            "trail",
            "trails",

            // city infrastructure
            "drainage",
            "public works",

            // waste
            "trash",
            "recycling",
            "waste"
    };

    // keywords to filter out false positive matches, politics, sports, and entertainment
    private static final String[] BLOCKED_KEYWORDS = {

            // politics
            "white house",
            "election",
            "elections",
            "campaign",
            "candidate",
            "democrat",
            "democratic party",
            "republican",
            "republican party",
            "congress",
            "congressman",
            "congresswoman",
            "senate race",
            "presidential",
            "primary election",

            // entertainment / gossip
            "celebrity",
            "hollywood",
            "actor",
            "actress",
            "singer",
            "reality star",
            "red carpet",
            "dating rumors",

            // sports
            "nfl",
            "nba",
            "mlb",
            "nhl",
            "ncaa",
            "football game",
            "basketball game",
            "baseball game",
            "soccer match",

            // non-civic events
            "concert",
            "music festival",
            "film festival"
    };

    private static final Map<String, CacheEntry> CACHE =
            new HashMap<>();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    public interface Callback {

        void onSuccess(
                @NonNull List<Article> articles
        );

        void onError(
                @NonNull String message
        );
    }

    public static class Article {

        private final String articleId;
        private final String title;
        private final String description;
        private final String link;
        private final String sourceName;
        private final long publishedAtMillis;

        Article(
                String articleId,
                String title,
                String description,
                String link,
                String sourceName,
                long publishedAtMillis
        ) {

            this.articleId = articleId;
            this.title = title;
            this.description = description;
            this.link = link;
            this.sourceName = sourceName;
            this.publishedAtMillis = publishedAtMillis;
        }

        public String getArticleId() {
            return articleId;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public String getLink() {
            return link;
        }

        public String getSourceName() {
            return sourceName;
        }

        public long getPublishedAtMillis() {
            return publishedAtMillis;
        }
    }

    public void fetchLocalNews(
            @NonNull String city,
            @NonNull Callback callback
    ) {

        if (
                BuildConfig.NEWSDATA_API_KEY == null
                        || BuildConfig.NEWSDATA_API_KEY
                        .trim()
                        .isEmpty()
        ) {

            callback.onError(
                    "NewsData API key is missing"
            );

            return;
        }

        String cleanedCity =
                city.trim();

        if (cleanedCity.isEmpty()) {

            callback.onError(
                    "Could not determine your city"
            );

            return;
        }

        String cacheKey =
                cleanedCity.toLowerCase(
                        Locale.US
                );

        CacheEntry cached;

        synchronized (CACHE) {

            cached =
                    CACHE.get(
                            cacheKey
                    );
        }

        if (
                cached != null
                        && System.currentTimeMillis()
                        - cached.createdAt
                        < CACHE_DURATION_MS
        ) {

            callback.onSuccess(
                    new ArrayList<>(
                            cached.articles
                    )
            );

            return;
        }

        executor.execute(
                () -> fetchFromNetwork(
                        cleanedCity,
                        cacheKey,
                        callback
                )
        );
    }

    private void fetchFromNetwork(
            @NonNull String city,
            @NonNull String cacheKey,
            @NonNull Callback callback
    ) {

        HttpURLConnection connection =
                null;

        try {

            // newsdata api request query
            String url =
                    ENDPOINT

                            + "?apikey="
                            + encode(
                            BuildConfig.NEWSDATA_API_KEY
                    )

                            + "&qInTitle="
                            + encode(
                            city
                    )

                            + "&country=us"

                            + "&language=en"

                            + "&category="
                            + encode(
                            "crime,environment"
                    )

                            + "&size=10"

                            + "&removeduplicate=1";

            connection =
                    (HttpURLConnection)
                            new java.net.URL(
                                    url
                            )
                                    .openConnection();

            connection.setRequestMethod(
                    "GET"
            );

            connection.setConnectTimeout(
                    12000
            );

            connection.setReadTimeout(
                    12000
            );

            connection.setRequestProperty(
                    "Accept",
                    "application/json"
            );

            int responseCode =
                    connection.getResponseCode();

            InputStream stream =
                    responseCode >= 200
                            && responseCode < 300

                            ? connection
                              .getInputStream()

                            : connection
                              .getErrorStream();

            String body =
                    readStream(
                            stream
                    );

            if (
                    responseCode < 200
                            || responseCode >= 300
            ) {

                String errorMessage =
                        extractErrorFromBody(
                                body
                        );

                postError(
                        callback,

                        errorMessage == null

                                ? "News service returned "
                                  + responseCode

                                : errorMessage
                );

                return;
            }

            JSONObject response =
                    new JSONObject(
                            body
                    );

            String status =
                    response.optString(
                            "status",
                            ""
                    );

            if (
                    !"success"
                            .equalsIgnoreCase(
                                    status
                            )
            ) {

                postError(
                        callback,
                        extractErrorMessage(
                                response
                        )
                );

                return;
            }

            JSONArray results =
                    response.optJSONArray(
                            "results"
                    );

            List<Article> articles =
                    new ArrayList<>();

            if (results != null) {

                for (
                        int i = 0;
                        i < results.length();
                        i++
                ) {

                    JSONObject item =
                            results.optJSONObject(
                                    i
                            );

                    if (item == null) {
                        continue;
                    }

                    String title =
                            nullableString(
                                    item,
                                    "title"
                            );

                    String link =
                            nullableString(
                                    item,
                                    "link"
                            );

                    if (
                            title == null
                                    || title.isEmpty()
                                    || link == null
                                    || link.isEmpty()
                    ) {

                        continue;
                    }

                    String description =
                            nullableString(
                                    item,
                                    "description"
                            );

                    // filter for strict civic relevance
                    if (
                            !isRelevantCivicArticle(
                                    title,
                                    description
                            )
                    ) {

                        continue;
                    }

                    String articleId =
                            nullableString(
                                    item,
                                    "article_id"
                            );

                    String sourceName =
                            nullableString(
                                    item,
                                    "source_name"
                            );

                    if (
                            sourceName == null
                                    || sourceName.isEmpty()
                    ) {

                        sourceName =
                                nullableString(
                                        item,
                                        "source_id"
                                );
                    }

                    if (
                            sourceName == null
                                    || sourceName.isEmpty()
                    ) {

                        sourceName =
                                "Local News";
                    }

                    long publishedAt =
                            parsePublishedTime(
                                    nullableString(
                                            item,
                                            "pubDate"
                                    ),

                                    nullableString(
                                            item,
                                            "pubDateTZ"
                                    )
                            );

                    articles.add(
                            new Article(
                                    articleId,
                                    title,
                                    description,
                                    link,
                                    sourceName,
                                    publishedAt
                            )
                    );
                }
            }

            Collections.sort(
                    articles,

                    (a, b) ->
                            Long.compare(
                                    b.getPublishedAtMillis(),
                                    a.getPublishedAtMillis()
                            )
            );

            synchronized (CACHE) {

                CACHE.put(
                        cacheKey,

                        new CacheEntry(
                                System.currentTimeMillis(),
                                articles
                        )
                );
            }

            mainHandler.post(
                    () -> callback.onSuccess(
                            new ArrayList<>(
                                    articles
                            )
                    )
            );

        } catch (
                Exception error
        ) {

            postError(
                    callback,

                    error.getMessage() == null

                            ? "Could not load local news"

                            : error.getMessage()
            );

        } finally {

            if (connection != null) {

                connection.disconnect();
            }
        }
    }

    private boolean isRelevantCivicArticle(
            @Nullable String title,
            @Nullable String description
    ) {

        String combined =
                (
                        safeString(
                                title
                        )
                                + " "
                                + safeString(
                                description
                        )
                )
                        .toLowerCase(
                                Locale.US
                        );

        for (
                String blocked
                : BLOCKED_KEYWORDS
        ) {

            if (
                    combined.contains(
                            blocked
                    )
            ) {

                return false;
            }
        }

        for (
                String keyword
                : CIVIC_KEYWORDS
        ) {

            if (
                    combined.contains(
                            keyword
                    )
            ) {

                return true;
            }
        }

        return false;
    }

    private String safeString(
            @Nullable String value
    ) {

        return value == null
                ? ""
                : value;
    }

    private void postError(
            @NonNull Callback callback,
            @NonNull String message
    ) {

        mainHandler.post(
                () -> callback.onError(
                        message
                )
        );
    }

    private String encode(
            @NonNull String value
    ) throws Exception {

        return URLEncoder.encode(
                value,
                "UTF-8"
        );
    }

    private String readStream(
            @Nullable InputStream stream
    ) throws Exception {

        if (stream == null) {
            return "";
        }

        StringBuilder builder =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        stream
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                builder.append(
                        line
                );
            }
        }

        return builder.toString();
    }

    @Nullable
    private String nullableString(
            @NonNull JSONObject object,
            @NonNull String key
    ) {

        Object raw =
                object.opt(
                        key
                );

        if (
                raw == null
                        || raw == JSONObject.NULL
        ) {

            return null;
        }

        String value =
                raw.toString()
                        .trim();

        if (
                value.isEmpty()
                        || "null"
                        .equalsIgnoreCase(
                                value
                        )
        ) {

            return null;
        }

        return value;
    }

    private long parsePublishedTime(
            @Nullable String value,
            @Nullable String timezone
    ) {

        if (
                value == null
                        || value.isEmpty()
        ) {

            return 0L;
        }

        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.US
                    );

            format.setTimeZone(
                    TimeZone.getTimeZone(

                            timezone == null
                                    || timezone.isEmpty()

                                    ? "UTC"

                                    : timezone
                    )
            );

            Date date =
                    format.parse(
                            value
                    );

            return date == null
                    ? 0L
                    : date.getTime();

        } catch (
                Exception ignored
        ) {

            return 0L;
        }
    }

    @Nullable
    private String extractErrorFromBody(
            @Nullable String body
    ) {

        if (
                body == null
                        || body.trim().isEmpty()
        ) {

            return null;
        }

        try {

            JSONObject response =
                    new JSONObject(
                            body
                    );

            return extractErrorMessage(
                    response
            );

        } catch (
                Exception ignored
        ) {

            return null;
        }
    }

    private String extractErrorMessage(
            @NonNull JSONObject response
    ) {

        String message =
                response.optString(
                        "message",
                        ""
                );

        if (!message.isEmpty()) {

            return message;
        }

        JSONObject results =
                response.optJSONObject(
                        "results"
                );

        if (results != null) {

            message =
                    results.optString(
                            "message",
                            ""
                    );

            if (!message.isEmpty()) {

                return message;
            }
        }

        return "Could not load local news";
    }

    public void shutdown() {

        executor.shutdownNow();
    }

    private static class CacheEntry {

        final long createdAt;

        final List<Article> articles;

        CacheEntry(
                long createdAt,
                List<Article> articles
        ) {

            this.createdAt =
                    createdAt;

            this.articles =
                    new ArrayList<>(
                            articles
                    );
        }
    }
}