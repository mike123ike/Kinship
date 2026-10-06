package com.pkg.civicfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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

    private static final String TAG =
            "LocalNewsRepository";

    private static final String ENDPOINT =
            "https://newsdata.io/api/1/latest";

    private static final long CACHE_DURATION_MS =
            20L * 60L * 1000L;

    // keep the actual newsdata q value under 100 characters.
    private static final int MAX_QUERY_CHARACTERS =
            100;

    // main civic/public-safety concepts.
    private static final String[] CIVIC_QUERY_TERMS = {
            "police",
            "fire",
            "crash",
            "road",
            "outage",
            "flood",
            "crime",
            "murder"
    };

    // major metro anchors. the closest city to the phone's gps is selected.
    private static final MajorCity[] MAJOR_CITIES = {

            new MajorCity("New York", "NY", 40.7128, -74.0060),
            new MajorCity("Los Angeles", "CA", 34.0522, -118.2437),
            new MajorCity("Chicago", "IL", 41.8781, -87.6298),
            new MajorCity("Houston", "TX", 29.7604, -95.3698),
            new MajorCity("Phoenix", "AZ", 33.4484, -112.0740),
            new MajorCity("Philadelphia", "PA", 39.9526, -75.1652),
            new MajorCity("San Antonio", "TX", 29.4241, -98.4936),
            new MajorCity("San Diego", "CA", 32.7157, -117.1611),
            new MajorCity("Dallas", "TX", 32.7767, -96.7970),
            new MajorCity("San Jose", "CA", 37.3382, -121.8863),

            new MajorCity("Austin", "TX", 30.2672, -97.7431),

            new MajorCity("Jacksonville", "FL", 30.3322, -81.6557),
            new MajorCity("Fort Worth", "TX", 32.7555, -97.3308),
            new MajorCity("Columbus", "OH", 39.9612, -82.9988),
            new MajorCity("Indianapolis", "IN", 39.7684, -86.1581),
            new MajorCity("Charlotte", "NC", 35.2271, -80.8431),
            new MajorCity("Seattle", "WA", 47.6062, -122.3321),
            new MajorCity("Denver", "CO", 39.7392, -104.9903),
            new MajorCity("Washington", "DC", 38.9072, -77.0369),
            new MajorCity("Nashville", "TN", 36.1627, -86.7816),
            new MajorCity("Oklahoma City", "OK", 35.4676, -97.5164),
            new MajorCity("El Paso", "TX", 31.7619, -106.4850),
            new MajorCity("Boston", "MA", 42.3601, -71.0589),
            new MajorCity("Portland", "OR", 45.5152, -122.6784),
            new MajorCity("Las Vegas", "NV", 36.1699, -115.1398),
            new MajorCity("Detroit", "MI", 42.3314, -83.0458),
            new MajorCity("Memphis", "TN", 35.1495, -90.0490),
            new MajorCity("Louisville", "KY", 38.2527, -85.7585),
            new MajorCity("Baltimore", "MD", 39.2904, -76.6122),
            new MajorCity("Milwaukee", "WI", 43.0389, -87.9065),
            new MajorCity("Albuquerque", "NM", 35.0844, -106.6504),
            new MajorCity("Tucson", "AZ", 32.2226, -110.9747),
            new MajorCity("Fresno", "CA", 36.7378, -119.7871),
            new MajorCity("Sacramento", "CA", 38.5816, -121.4944),
            new MajorCity("Kansas City", "MO", 39.0997, -94.5786),
            new MajorCity("Atlanta", "GA", 33.7490, -84.3880),
            new MajorCity("Miami", "FL", 25.7617, -80.1918),
            new MajorCity("Minneapolis", "MN", 44.9778, -93.2650),
            new MajorCity("Cleveland", "OH", 41.4993, -81.6944),
            new MajorCity("New Orleans", "LA", 29.9511, -90.0715),
            new MajorCity("Tampa", "FL", 27.9506, -82.4572),
            new MajorCity("Orlando", "FL", 28.5383, -81.3792),
            new MajorCity("Pittsburgh", "PA", 40.4406, -79.9959),
            new MajorCity("Cincinnati", "OH", 39.1031, -84.5120),
            new MajorCity("St. Louis", "MO", 38.6270, -90.1994),
            new MajorCity("Raleigh", "NC", 35.7796, -78.6382),
            new MajorCity("Richmond", "VA", 37.5407, -77.4360),
            new MajorCity("Salt Lake City", "UT", 40.7608, -111.8910),
            new MajorCity("Birmingham", "AL", 33.5186, -86.8104),
            new MajorCity("Buffalo", "NY", 42.8864, -78.8784),
            new MajorCity("Rochester", "NY", 43.1566, -77.6088),
            new MajorCity("Providence", "RI", 41.8240, -71.4128),
            new MajorCity("Hartford", "CT", 41.7658, -72.6734),
            new MajorCity("Omaha", "NE", 41.2565, -95.9345),
            new MajorCity("Tulsa", "OK", 36.1540, -95.9928),
            new MajorCity("Little Rock", "AR", 34.7465, -92.2896),
            new MajorCity("Boise", "ID", 43.6150, -116.2023),
            new MajorCity("Spokane", "WA", 47.6588, -117.4260),
            new MajorCity("Charleston", "SC", 32.7765, -79.9311),
            new MajorCity("Columbia", "SC", 34.0007, -81.0348),
            new MajorCity("Greenville", "SC", 34.8526, -82.3940),
            new MajorCity("Knoxville", "TN", 35.9606, -83.9207),
            new MajorCity("Chattanooga", "TN", 35.0456, -85.3097),
            new MajorCity("Baton Rouge", "LA", 30.4515, -91.1871),
            new MajorCity("Jackson", "MS", 32.2988, -90.1848),
            new MajorCity("Des Moines", "IA", 41.5868, -93.6250),
            new MajorCity("Wichita", "KS", 37.6872, -97.3301),
            new MajorCity("Colorado Springs", "CO", 38.8339, -104.8214),
            new MajorCity("Virginia Beach", "VA", 36.8529, -75.9780),
            new MajorCity("Greensboro", "NC", 36.0726, -79.7920),
            new MajorCity("Madison", "WI", 43.0731, -89.4012),
            new MajorCity("Grand Rapids", "MI", 42.9634, -85.6681),
            new MajorCity("Honolulu", "HI", 21.3069, -157.8583),
            new MajorCity("Anchorage", "AK", 61.2181, -149.9003)
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

            this.articleId =
                    articleId;

            this.title =
                    title;

            this.description =
                    description;

            this.link =
                    link;

            this.sourceName =
                    sourceName;

            this.publishedAtMillis =
                    publishedAtMillis;
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
            double latitude,
            double longitude,
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


        if (
                latitude < -90.0
                        || latitude > 90.0
                        || longitude < -180.0
                        || longitude > 180.0
        ) {

            callback.onError(
                    "Could not determine a valid location"
            );

            return;
        }


        MajorCity majorCity =
                findNearestMajorCity(
                        latitude,
                        longitude
                );


        if (
                majorCity == null
        ) {

            callback.onError(
                    "Could not determine a nearby major city"
            );

            return;
        }


        String cacheKey =
                (
                        majorCity.name
                                + "|"
                                + majorCity.stateCode
                )
                        .toLowerCase(
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
                        majorCity,
                        cacheKey,
                        callback
                )
        );
    }


    private void fetchFromNetwork(
            @NonNull MajorCity majorCity,
            @NonNull String cacheKey,
            @NonNull Callback callback
    ) {

        HttpURLConnection connection =
                null;


        try {

            String searchQuery =
                    buildSearchQuery(
                            majorCity
                    );


            Log.d(
                    TAG,
                    "NewsData major-city query: "
                            + searchQuery
                            + " ("
                            + searchQuery.length()
                            + " chars)"
            );


            String url =
                    ENDPOINT

                            + "?apikey="
                            + encode(
                            BuildConfig.NEWSDATA_API_KEY
                    )

                            + "&q="
                            + encode(
                            searchQuery
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


            if (
                    results != null
            ) {

                for (
                        int i = 0;
                        i < results.length();
                        i++
                ) {

                    JSONObject item =
                            results.optJSONObject(
                                    i
                            );


                    if (
                            item == null
                    ) {

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

            if (
                    connection != null
            ) {

                connection.disconnect();
            }
        }
    }


    @NonNull
    private String buildSearchQuery(
            @NonNull MajorCity majorCity
    ) {

        String allTerms =
                String.join(
                        " OR ",
                        CIVIC_QUERY_TERMS
                );


        String stateName =
                stateNameForCode(
                        majorCity.stateCode
                );


        // prefer the full state name when the complete query stays below 100 characters.
        if (
                stateName != null
        ) {

            String fullStateQuery =
                    "\""
                            + majorCity.name
                            + "\" AND \""
                            + stateName
                            + "\" AND ("
                            + allTerms
                            + ")";


            if (
                    fullStateQuery.length()
                            <= MAX_QUERY_CHARACTERS
            ) {

                return fullStateQuery;
            }
        }


        // for unusually long names use the two-letter state code and add terms until the query would exceed 100 characters.
        String prefix =
                "\""
                        + majorCity.name
                        + "\" AND "
                        + majorCity.stateCode
                        + " AND (";


        StringBuilder terms =
                new StringBuilder();


        for (
                String term
                : CIVIC_QUERY_TERMS
        ) {

            String candidateTerms =
                    terms.length() == 0

                            ? term

                            : terms
                              + " OR "
                              + term;


            String candidateQuery =
                    prefix
                            + candidateTerms
                            + ")";


            if (
                    candidateQuery.length()
                            > MAX_QUERY_CHARACTERS
            ) {

                break;
            }


            terms.setLength(
                    0
            );


            terms.append(
                    candidateTerms
            );
        }


        if (
                terms.length() == 0
        ) {

            return "\""
                    + majorCity.name
                    + "\" AND "
                    + majorCity.stateCode;
        }


        return prefix
                + terms
                + ")";
    }


    @Nullable
    private String stateNameForCode(
            @NonNull String stateCode
    ) {

        switch (
                stateCode
        ) {

            case "AL":
                return "Alabama";

            case "AK":
                return "Alaska";

            case "AZ":
                return "Arizona";

            case "AR":
                return "Arkansas";

            case "CA":
                return "California";

            case "CO":
                return "Colorado";

            case "CT":
                return "Connecticut";

            case "DC":
                return "District of Columbia";

            case "FL":
                return "Florida";

            case "GA":
                return "Georgia";

            case "HI":
                return "Hawaii";

            case "ID":
                return "Idaho";

            case "IL":
                return "Illinois";

            case "IN":
                return "Indiana";

            case "IA":
                return "Iowa";

            case "KS":
                return "Kansas";

            case "KY":
                return "Kentucky";

            case "LA":
                return "Louisiana";

            case "MA":
                return "Massachusetts";

            case "MD":
                return "Maryland";

            case "MI":
                return "Michigan";

            case "MN":
                return "Minnesota";

            case "MS":
                return "Mississippi";

            case "MO":
                return "Missouri";

            case "NE":
                return "Nebraska";

            case "NV":
                return "Nevada";

            case "NM":
                return "New Mexico";

            case "NY":
                return "New York";

            case "NC":
                return "North Carolina";

            case "OH":
                return "Ohio";

            case "OK":
                return "Oklahoma";

            case "OR":
                return "Oregon";

            case "PA":
                return "Pennsylvania";

            case "RI":
                return "Rhode Island";

            case "SC":
                return "South Carolina";

            case "TN":
                return "Tennessee";

            case "TX":
                return "Texas";

            case "UT":
                return "Utah";

            case "VA":
                return "Virginia";

            case "WA":
                return "Washington";

            case "WI":
                return "Wisconsin";

            default:
                return null;
        }
    }


    @Nullable
    private MajorCity findNearestMajorCity(
            double latitude,
            double longitude
    ) {

        MajorCity nearest =
                null;


        double nearestDistanceKm =
                Double.MAX_VALUE;


        for (
                MajorCity city
                : MAJOR_CITIES
        ) {

            double distanceKm =
                    haversineKm(
                            latitude,
                            longitude,
                            city.latitude,
                            city.longitude
                    );


            if (
                    distanceKm
                            < nearestDistanceKm
            ) {

                nearestDistanceKm =
                        distanceKm;

                nearest =
                        city;
            }
        }


        if (
                nearest != null
        ) {

            Log.d(
                    TAG,
                    "Nearest major city: "
                            + nearest.name
                            + ", "
                            + nearest.stateCode
                            + " ("
                            + String.format(
                            Locale.US,
                            "%.1f km",
                            nearestDistanceKm
                    )
                            + ")"
            );
        }


        return nearest;
    }


    private double haversineKm(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double earthRadiusKm =
                6371.0088;


        double lat1 =
                Math.toRadians(
                        latitude1
                );


        double lat2 =
                Math.toRadians(
                        latitude2
                );


        double deltaLat =
                Math.toRadians(
                        latitude2
                                - latitude1
                );


        double deltaLon =
                Math.toRadians(
                        longitude2
                                - longitude1
                );


        double a =
                Math.sin(
                        deltaLat / 2.0
                )
                        * Math.sin(
                        deltaLat / 2.0
                )

                        + Math.cos(
                        lat1
                )
                        * Math.cos(
                        lat2
                )
                        * Math.sin(
                        deltaLon / 2.0
                )
                        * Math.sin(
                        deltaLon / 2.0
                );


        double c =
                2.0
                        * Math.atan2(
                        Math.sqrt(
                                a
                        ),
                        Math.sqrt(
                                1.0 - a
                        )
                );


        return earthRadiusKm
                * c;
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


    @NonNull
    private String encode(
            @NonNull String value
    ) throws Exception {

        return URLEncoder.encode(
                value,
                "UTF-8"
        );
    }


    @NonNull
    private String readStream(
            @Nullable InputStream stream
    ) throws Exception {

        if (
                stream == null
        ) {

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
                    (
                            line =
                                    reader.readLine()
                    )
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


    @NonNull
    private String extractErrorMessage(
            @NonNull JSONObject response
    ) {

        String message =
                nullableString(
                        response,
                        "message"
                );


        if (
                message != null
        ) {

            return message;
        }


        JSONObject results =
                response.optJSONObject(
                        "results"
                );


        if (
                results != null
        ) {

            message =
                    nullableString(
                            results,
                            "message"
                    );


            if (
                    message != null
            ) {

                return message;
            }
        }


        return "Could not load local news";
    }


    @Nullable
    private String extractErrorFromBody(
            @Nullable String body
    ) {

        if (
                body == null
                        || body
                        .trim()
                        .isEmpty()
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


    private long parsePublishedTime(
            @Nullable String pubDate,
            @Nullable String pubDateTimezone
    ) {

        if (
                pubDate == null
                        || pubDate.isEmpty()
        ) {

            return 0L;
        }


        try {

            SimpleDateFormat format =
                    new SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss",
                            Locale.US
                    );


            if (
                    pubDateTimezone != null
                            && !pubDateTimezone
                            .trim()
                            .isEmpty()
            ) {

                format.setTimeZone(
                        TimeZone.getTimeZone(
                                pubDateTimezone
                        )
                );


            } else {

                format.setTimeZone(
                        TimeZone.getTimeZone(
                                "UTC"
                        )
                );
            }


            Date date =
                    format.parse(
                            pubDate
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


    public void shutdown() {

        executor.shutdownNow();
    }


    private static class CacheEntry {

        final long createdAt;
        final List<Article> articles;


        CacheEntry(
                long createdAt,
                @NonNull List<Article> articles
        ) {

            this.createdAt =
                    createdAt;

            this.articles =
                    new ArrayList<>(
                            articles
                    );
        }
    }


    private static class MajorCity {

        final String name;
        final String stateCode;
        final double latitude;
        final double longitude;


        MajorCity(
                @NonNull String name,
                @NonNull String stateCode,
                double latitude,
                double longitude
        ) {

            this.name =
                    name;

            this.stateCode =
                    stateCode;

            this.latitude =
                    latitude;

            this.longitude =
                    longitude;
        }
    }
}