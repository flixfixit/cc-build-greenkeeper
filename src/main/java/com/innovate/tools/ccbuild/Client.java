package com.innovate.tools.ccbuild;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public class Client {
    private final String baseUrl;
    private final String token;
    private final String subscription;
    private final String buildsPath;
    private final String deleteTemplate;

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper om = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public Client(String baseUrl, String token, String subscription, String buildsPath, String deleteTemplate) {
        this.baseUrl = trimEnd(baseUrl);
        this.token = token;
        this.subscription = subscription;
        this.buildsPath = buildsPath;
        this.deleteTemplate = deleteTemplate;
    }

    public List<Models.Build> listAllBuilds(int pageSize) throws IOException {
        List<Models.Build> out = new ArrayList<>();
        int page = 0;
        while (true) {
            List<Models.Build> chunk = listBuilds(page, pageSize);
            out.addAll(chunk);
            if (chunk.size() < pageSize) {
                break; // simple pagination heuristic
            }
            page++;
        }
        return out;
    }

    public List<Models.Build> listBuilds(int page, int pageSize) throws IOException {
        String resolvedPath = subscriptionScopedPath(String.format(buildsPath, enc(subscription)));
        //String url = baseUrl + resolvedPath + "?page=" + page + "&pageSize=" + pageSize;
        int skip = page * pageSize;
        String url = baseUrl + resolvedPath + "?$top=" + pageSize + "&$skip=" + skip + "&statusNot=DELETED&$count=true&$orderby=buildStartTimestamp%%20desc";
        //?statusNot=DELETED&$top=12&$skip=0&$count=true&$orderby=buildStartTimestamp%20desc
        //?statusNot=DELETED&$top=12&$skip=12&$count=true&$orderby=buildStartTimestamp%20desc
        //?statusNot=DELETED&$top=12&$skip=24&$count=true&$orderby=buildStartTimestamp%20desc

        Request req = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IOException("HTTP " + res.code() + " listing builds");
            }
            String body = Objects.requireNonNull(res.body()).string();
            // Erwartet entweder {"items": [...]} oder ein nacktes Array; beides unterstützen
            JsonNode n = om.readTree(body);
            List<Models.Build> list;
            if (n.isArray()) {
                list = om.readValue(body, new TypeReference<>() {});
            } else {
                JsonNode items = n.get("items");
                if (items == null) items = n.get("value");
                if (items == null) items = n.get("data");
                if (items == null || !items.isArray()) {
                    throw new IOException("Unerwartetes Listenformat");
                }
                list = om.convertValue(items, new TypeReference<>() {});
            }
            // Defensive parsing: fehlende Felder auffüllen
            for (Models.Build b : list) {
                if (b.createdAt() == null) {
                    // versuche alternative Felder
                    Instant created = Util.tryParseInstantFromMap(b.raw());
                    b.raw().put("_createdAtFallback", created != null ? created.toString() : null);
                }
            }
            return list;
        }
    }

    public void deleteBuild(String buildCode) throws IOException {
        String resolvedPath = subscriptionScopedPath(String.format(deleteTemplate, enc(subscription), enc(buildCode)));
        String url = baseUrl + resolvedPath;
        Request req = new Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();

        try (Response res = http.newCall(req).execute()) {
            if (!res.isSuccessful()) {
                throw new IOException("HTTP " + res.code() + " deleting build");
            }
        }
    }

    private static String trimEnd(String s) {
        return s.replaceAll("/+$", "");
    }

    private String subscriptionScopedPath(String rawPath) {
        String normalized = path(rawPath);
        if (normalized.startsWith("/v2/subscriptions/")) {
            return normalized; // already a fully qualified path
        }
        return "/v2/subscriptions/" + enc(subscription) + normalized;
    }

    private static String path(String p) {
        return p.startsWith("/") ? p : "/" + p;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
