package com.RobinNotBad.BiliClient.util;

import android.annotation.SuppressLint;
import android.os.Build;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.Inflater;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 被 luern0313 创建于 2019/10/13.
 * #以下代码来源于腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class NetWorkUtil {
    private static final AtomicReference<OkHttpClient> INSTANCE = new AtomicReference<>();

    public static class Inet4Selector implements Dns {
        @NonNull
        @Override
        public List<InetAddress> lookup(@NonNull String hostname) throws UnknownHostException {
            List<InetAddress> hosts = Dns.SYSTEM.lookup(hostname);
            List<InetAddress> inet4Hosts = new ArrayList<>();
            for (InetAddress host: hosts) {
                if (host.getAddress().length == 4) inet4Hosts.add(host);
            }
            return inet4Hosts;
        }
    }

    public static OkHttpClient getOkHttpInstance() {
        while (INSTANCE.get() == null) {
            INSTANCE.compareAndSet(null, setOkHttpSsl(new OkHttpClient.Builder())
                    .followRedirects(false)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        Response response = chain.proceed(request);
                        RedirectHandler handler;
                        String location = response.header("Location");
                        boolean isSslRedirect = false;
                        try {
                            isSslRedirect = location != null && !request.isHttps() && new URI(location).getScheme().equalsIgnoreCase("https") && request.url().host().equalsIgnoreCase(new URI(location).getHost());
                        }
                        catch (URISyntaxException ignored) {}

                        if (response.isRedirect() && location != null) {
                            if (request.url().host().equals("b23.tv") && !isSslRedirect && (handler = request.tag(RedirectHandler.class)) != null) {
                                handler.handleRedirect(location);
                            }
                            else {
                                Request newRequest = request.newBuilder()
                                        .url(location)
                                        .build();
                                return chain.proceed(newRequest);
                            }
                        }
                        return response;
                    })
                    .addInterceptor(new CookieSaveInterceptor())
                    .dns(new Inet4Selector())
                    .pingInterval(8, TimeUnit.SECONDS)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(16, TimeUnit.SECONDS).build());
        }
        return INSTANCE.get();
    }

    public synchronized static OkHttpClient.Builder setOkHttpSsl(OkHttpClient.Builder okhttpBuilder) {
        if (Build.VERSION.SDK_INT > 22) return okhttpBuilder;
        try {
            @SuppressLint("CustomX509TrustManager") final X509TrustManager trustAllCert =
                    new X509TrustManager() {
                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @SuppressLint("TrustAllX509TrustManager")
                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    };
            final SSLSocketFactory sslSocketFactory = new SSLSocketFactoryCompat(trustAllCert);
            okhttpBuilder.sslSocketFactory(sslSocketFactory, trustAllCert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return okhttpBuilder;
    }

    public static JSONObject getJson(String url) throws IOException, JSONException {
        try (ResponseBody body = get(url).body()) {
            if (body != null) return new JSONObject(body.string());
            else throw new JSONException("在访问" + url + "时返回数据为空");
        }
    }

    public static JSONObject getJson(String url, ArrayList<String> headers) throws IOException, JSONException {
        try (ResponseBody body = get(url, headers).body()) {
            if (body != null) return new JSONObject(body.string());
            else throw new JSONException("在访问" + url + "时返回数据为空");
        }
    }

    public static Response get(String url) throws IOException {
        return get(url, webHeaders);
    }

    public static Response get(String url, ArrayList<String> headers) throws IOException {
        return get(url, headers, null);
    }

    public static Response get(String url, ArrayList<String> headers, RedirectHandler redirectHandler) throws IOException {
        Logu.d("get-url", url);
        OkHttpClient client = getOkHttpInstance();
        Request.Builder requestBuilder = new Request.Builder().url(url).get();

        if (headers != null) {
            for (int i = 0; i < headers.size(); i += 2) {
                requestBuilder.addHeader(headers.get(i), headers.get(i + 1));
            }
        }

        // 确保 Cookie 存在
        String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookie != null && cookie.length() > 0) {
            requestBuilder.addHeader("Cookie", cookie);
        }

        if (redirectHandler != null) {
            requestBuilder.tag(RedirectHandler.class, redirectHandler);
        }

        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers, String contentType) throws IOException {
        Logu.d("post-url", url);
        Logu.d("post-data", data);
        OkHttpClient client = getOkHttpInstance();
        RequestBody body = RequestBody.create(MediaType.parse(contentType + "; charset=utf-8"), data);
        Request.Builder requestBuilder = new Request.Builder().url(url).post(body);

        if (headers != null) {
            for (int i = 0; i < headers.size(); i += 2) {
                String key = headers.get(i);
                String val = headers.get(i + 1);
                if (key.equalsIgnoreCase("Content-Type")) val = contentType;
                requestBuilder.addHeader(key, val);
            }
        }

        // 确保 Cookie 存在
        String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        if (cookie != null && cookie.length() > 0) {
            requestBuilder.addHeader("Cookie", cookie);
        }

        Request request = requestBuilder.build();
        return client.newCall(request).execute();
    }

    public static Response post(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/x-www-form-urlencoded");
    }

    public static Response postJson(String url, String data, List<String> headers) throws IOException {
        return post(url, data, headers, "application/json");
    }

    public static Response postJson(String url, String data) throws IOException {
        return post(url, data, webHeaders, "application/json");
    }

    public static Response post(String url, String data) throws IOException {
        return post(url, data, webHeaders);
    }


    public static byte[] readStream(InputStream inStream) throws IOException {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = inStream.read(buffer)) != -1) {
            outStream.write(buffer, 0, len);
        }
        outStream.close();
        inStream.close();
        return outStream.toByteArray();
    }

    public static byte[] uncompress(byte[] inputByte) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(inputByte.length);
        try {
            Inflater inflater = new Inflater(true);
            inflater.setInput(inputByte);
            byte[] buffer = new byte[4 * 1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] output = outputStream.toByteArray();
        outputStream.close();
        return output;
    }

    public static String getInfoFromCookie(String name, String cookie) {
        if (cookie == null || cookie.length() == 0) return "";
        String[] cookies = cookie.split("; ");
        for (String i : cookies) {
            if (i.contains(name + "="))
                return i.substring(name.length() + 1);
        }
        return "";
    }

    private static void saveCookiesFromResponse(Response response) {
        List<String> newCookies = response.headers("Set-Cookie");

        if (newCookies.isEmpty()) return;
        String cookiesStr = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
        ArrayList<String> oldCookies = (cookiesStr == null || cookiesStr.length() == 0 ? new ArrayList<String>() : new ArrayList<String>(Arrays.asList(cookiesStr.split("; "))));

        for (String newCookie : newCookies) {
            Cookies cookies = new Cookies(newCookie);
            if (cookies.containsKey("Domain") && !cookies.get("Domain").endsWith("bilibili.com"))
                continue;

            int index = newCookie.indexOf("; ");
            if (index != -1) newCookie = newCookie.substring(0, index);

            index = newCookie.indexOf("=") + 1;
            if (index == 0) continue;

            String key = newCookie.substring(0, index);
            Logu.d("newCookie", newCookie);

            boolean added = false;
            for (int i = 0; i < oldCookies.size(); i++) {
                String oldCookie = oldCookies.get(i);
                if (oldCookie.contains(key)) {
                    oldCookies.set(i, newCookie);
                    added = true;
                    break;
                }
            }
            if (!added) {
                oldCookies.add(newCookie);
            }
        }

        StringBuilder setCookies = new StringBuilder();
        for (String setCookie : oldCookies) {
            setCookies.append(setCookie).append("; ");
        }
        if (setCookies.length() >= 2) {
            Logu.d("save-result", setCookies.substring(0, setCookies.length() - 2));
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, setCookies.substring(0, setCookies.length() - 2));
            refreshHeaders();
        }
    }

    public static void putCookie(String key, String val) {
        synchronized (NetWorkUtil.class) {
            Cookies cookies = new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
            cookies.set(key, val);
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    public static void setCookies(Cookies cookies) {
        synchronized (NetWorkUtil.class) {
            SharedPreferencesUtil.putString(SharedPreferencesUtil.cookies, cookies.toString());
            refreshHeaders();
        }
    }

    public static Cookies getCookies() {
        synchronized (NetWorkUtil.class) {
            return new Cookies(SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, ""));
        }
    }

    public static final String USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.6261.95 Safari/537.36";
    public static final ArrayList<String> webHeaders = new ArrayList<String>();

    static {
        webHeaders.add("Origin");
        webHeaders.add("https://www.bilibili.com");

        webHeaders.add("Referer");
        webHeaders.add("https://www.bilibili.com/");

        webHeaders.add("User-Agent");
        webHeaders.add(USER_AGENT_WEB);

        webHeaders.add("Sec-Ch-Ua");
        webHeaders.add("\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Google Chrome\";v=\"122\"");

        webHeaders.add("Sec-Ch-Ua-Platform");
        webHeaders.add("\"Windows\"");

        webHeaders.add("Sec-Ch-Ua-Mobile");
        webHeaders.add("?0");
    }

    public static void refreshHeaders() {
        synchronized (NetWorkUtil.class) {
            // 移除旧的 Cookie 头
            int cookieIndex = -1;
            for (int i = 0; i < webHeaders.size(); i++) {
                if ("Cookie".equals(webHeaders.get(i))) {
                    cookieIndex = i;
                    break;
                }
            }
            if (cookieIndex >= 0) {
                webHeaders.remove(cookieIndex);
                webHeaders.remove(cookieIndex);
            }

            // 添加新的 Cookie
            String cookie = SharedPreferencesUtil.getString(SharedPreferencesUtil.cookies, "");
            if (cookie != null && cookie.length() > 0) {
                webHeaders.add("Cookie");
                webHeaders.add(cookie);
            }
        }
    }

    public static class FormData {
        private final Map<String, String> data;
        private boolean isUrlParam;

        public FormData() {
            data = new HashMap<String, String>();
        }

        public FormData remove(String key) {
            data.remove(key);
            return this;
        }

        public FormData put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public FormData setUrlParam(boolean isUrlParam) {
            this.isUrlParam = isUrlParam;
            return this;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            if (isUrlParam) sb.append("?");

            try {
                for (String key : data.keySet()) {
                    if (sb.length() > (isUrlParam ? 1 : 0)) {
                        sb.append("&");
                    }
                    sb.append(URLEncoder.encode(key, "UTF-8"));
                    sb.append("=");
                    sb.append(URLEncoder.encode(data.get(key), "UTF-8"));
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }

            return sb.toString();
        }
    }

    public interface RedirectHandler {
        void handleRedirect(String location);
    }

    private static class CookieSaveInterceptor implements Interceptor {
        @NonNull
        @Override
        public Response intercept(Chain chain) throws IOException {
            Response response = chain.proceed(chain.request());
            saveCookiesFromResponse(response);
            return response;
        }
    }
}