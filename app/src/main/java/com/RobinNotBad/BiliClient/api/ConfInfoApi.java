package com.RobinNotBad.BiliClient.api;

import android.net.Uri;

import com.RobinNotBad.BiliClient.util.FileUtil;
import com.RobinNotBad.BiliClient.util.Logu;
import com.RobinNotBad.BiliClient.util.NetWorkUtil;
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil;
import com.RobinNotBad.BiliClient.util.ToolsUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import okhttp3.HttpUrl;

/**
 * 被 luern0313 创建于 2019/8/25.
 * (人尽皆知的)绝 · 密 · 档 · 案
 * #以下代码修改自腕上哔哩的开源项目，感谢开源者做出的贡献！
 */

public class ConfInfoApi {

    /*
    这里是WBI签名校验
    https://socialsisteryi.github.io/bilibili-API-collect/docs/misc/sign/wbi.html#wbi-%E7%AD%BE%E5%90%8D%E7%AE%97%E6%B3%95
     */
    private static final int[] MIXIN_KEY_ENC_TAB = {46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
            36, 20, 34, 44, 52};

    public static String getWBIRawKey() throws IOException, JSONException {
        JSONObject getJson = NetWorkUtil.getJson("https://api.bilibili.com/x/web-interface/nav");
        JSONObject wbi_img = getJson.getJSONObject("data").getJSONObject("wbi_img");
        String img_key = FileUtil.getFileFirstName(FileUtil.getFileNameFromLink(wbi_img.getString("img_url")));
        String sub_key = FileUtil.getFileFirstName(FileUtil.getFileNameFromLink(wbi_img.getString("sub_url")));
        return img_key + sub_key;
    }

    public static String getWBIMixinKey(String raw_key) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            key.append(raw_key.charAt(MIXIN_KEY_ENC_TAB[i]));
        }
        return key.toString();
    }

    public static String signWBI(String url) throws JSONException, IOException {
        // 1. 获取混合密钥
        String mixin_key;
        int curr = getDateCurr();
        if (SharedPreferencesUtil.getInt("last_wbi", 0) < curr) {
            Logu.d("检查WBI");
            SharedPreferencesUtil.putInt("last_wbi", curr);
            mixin_key = ConfInfoApi.getWBIMixinKey(ConfInfoApi.getWBIRawKey());
            SharedPreferencesUtil.putString("wbi_mixin_key", mixin_key);
        } else {
            mixin_key = SharedPreferencesUtil.getString("wbi_mixin_key", "");
        }

        // 如果混合密钥为空，强制刷新
        if (mixin_key == null || mixin_key.length() == 0) {
            mixin_key = ConfInfoApi.getWBIMixinKey(ConfInfoApi.getWBIRawKey());
            SharedPreferencesUtil.putString("wbi_mixin_key", mixin_key);
        }

        // 2. 解析 URL
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            Logu.e("ConfInfoApi", "URL解析失败: " + url);
            return url;
        }

        // 3. 获取所有参数，用 TreeMap 排序
        TreeMap<String, String> sortedParams = new TreeMap<>();
        for (String paramName : httpUrl.queryParameterNames()) {
            String paramValue = httpUrl.queryParameter(paramName);
            if (paramValue != null) {
                sortedParams.put(paramName, paramValue);
            }
        }

        // 4. 添加 wts 参数
        String wts = String.valueOf(System.currentTimeMillis() / 1000);
        sortedParams.put("wts", wts);

        // 5. 构建排序后的参数字符串
        StringBuilder sortedQuery = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (sortedQuery.length() > 0) {
                sortedQuery.append("&");
            }
            sortedQuery.append(entry.getKey()).append("=").append(entry.getValue());
        }

        // 6. 计算签名
        String calcStr = sortedQuery.toString() + mixin_key;
        Logu.d("ConfInfoApi", "待签名字符串: " + calcStr);
        String w_rid = ToolsUtil.md5(calcStr);

        // 7. 构建最终 URL
        HttpUrl.Builder builder = httpUrl.newBuilder();
        // 移除所有原有参数
        for (String paramName : httpUrl.queryParameterNames()) {
            builder.removeAllQueryParameters(paramName);
        }
        // 添加排序后的参数
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            builder.addQueryParameter(entry.getKey(), entry.getValue());
        }
        // 添加签名
        builder.addQueryParameter("w_rid", w_rid);

        String finalUrl = builder.build().toString();
        Logu.d("ConfInfoApi", "签名后URL: " + finalUrl);
        return finalUrl;
    }

    public static int getDateCurr() {
        Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.YEAR) * 10000 + calendar.get(Calendar.MONTH) * 100 + calendar.get(Calendar.DATE);
    }
}