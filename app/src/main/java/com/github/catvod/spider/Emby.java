package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Emby extends Spider {
    private String host;

    private static String staticDeviceId = "";
    private static String staticDeviceName = "";
    private static String staticApiKey = "";
    private static String staticUserId = "";

    @Override
    public void init(Context context, String extend) throws Exception {
        if (extend.startsWith("http")) {
            extend = OkHttp.string(extend);
        }
        JSONObject json = new JSONObject(extend);
        host = json.optString("url");
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }

        if (TextUtils.isEmpty(staticDeviceId)) {
            staticDeviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(staticDeviceId)) {
                staticDeviceId = "b687aa7a26d0ac85";
            }
        }

        if (TextUtils.isEmpty(staticDeviceName)) {
            staticDeviceName = Build.MODEL;
            if (TextUtils.isEmpty(staticDeviceName)) {
                staticDeviceName = "Android Device"; // 稳健性防空兜底
            }
        }        

        String token = json.optString("token");
        String username = json.optString("username");
        String password = json.optString("password");

        if (!TextUtils.isEmpty(token)) {
            staticApiKey = token;
            fetchDefaultUserId();
        } else if (!TextUtils.isEmpty(username)) {
            loginByPassword(username, password);
        }
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        StringBuilder auth = new StringBuilder("Emby ");

        auth.append("UserId=\"")
            .append(staticUserId)
            .append("\", ")
            .append("Client=\"Android\", Device=\"")
            .append(staticDeviceName)
            .append("\", DeviceId=\"")
            .append(staticDeviceId)
            .append("\", Version=\"1.0.0\"");

        if (!TextUtils.isEmpty(staticApiKey)) {
            auth.append(", Token=\"").append(staticApiKey).append("\"");
        }

        headers.put("X-Emby-Authorization", auth.toString());
        return headers;
    }

    private void loginByPassword(String username, String password) {
        try {
            String url = host + "/emby/Users/AuthenticateByName";

            JSONObject body = new JSONObject();
            body.put("Username", username);
            body.put("Password", password);
            body.put("Pw", password);

            String res = OkHttp.post(url, body.toString(), getHeaders()).getBody();
            if (!TextUtils.isEmpty(res)) {
                JSONObject obj = new JSONObject(res);
                staticApiKey = obj.optString("AccessToken");

                JSONObject userObj = obj.optJSONObject("User");
                if (userObj != null) {
                    staticUserId = userObj.optString("Id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fetchDefaultUserId() {
        try {
            String userUrl = host + "/emby/Users?api_key=" + staticApiKey;
            String userRes = OkHttp.string(userUrl, getHeaders());
            if (!TextUtils.isEmpty(userRes)) {
                JSONArray userArray = new JSONArray(userRes);
                if (userArray.length() > 0) {
                    staticUserId = userArray.getJSONObject(0).optString("Id");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String url = host + "/emby/Library/VirtualFolders?api_key=" + staticApiKey;
        String jsonStr = OkHttp.string(url, getHeaders());
        if (TextUtils.isEmpty(jsonStr)) return Result.get().classes(classes).string();
        JSONArray array = new JSONArray(jsonStr);
        for (int i = 0; i < array.length(); i++) {
            JSONObject folder = array.getJSONObject(i);
            String name = folder.optString("Name");
            String id = folder.optString("ItemId");
            classes.add(new Class(id, name));
        }
        return Result.get().classes(classes).string();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int limit = 30;
        int page = Integer.parseInt(pg);
        int startIndex = (page - 1) * limit;

        String url = host + "/emby/Items?ParentId=" + tid + "&StartIndex=" + startIndex + "&Limit=" + limit 
                + "&Fields=PrimaryImageAspectRatio,Overview&Recursive=true&IncludeItemTypes=Movie,Series&api_key=" + staticApiKey;
        
        String jsonStr = OkHttp.string(url, getHeaders());
        if (TextUtils.isEmpty(jsonStr)) return Result.get().string();
        
        JSONObject response = new JSONObject(jsonStr);
        JSONArray items = response.optJSONArray("Items");

        List<Vod> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String type = item.optString("Type");
                Vod vod = new Vod();
                vod.setVodId(item.optString("Id") + "_" + type);
                vod.setVodName(item.optString("Name"));
                vod.setVodPic(host + "/emby/Items/" + item.optString("Id") + "/Images/Primary?api_key=" + staticApiKey);
                vod.setVodRemarks("Series".equals(type) ? "剧集" : "电影");
                list.add(vod);
            }
        }
        int totalRecordCount = response.optInt("TotalRecordCount", 0);
        int pageCount = (int) Math.ceil((double) totalRecordCount / limit);
        return Result.get().vod(list).page(page, pageCount, limit, totalRecordCount).string();
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String rawId = ids.get(0);
        String[] parts = rawId.split("_");
        String id = parts[0];
        String type = parts.length > 1 ? parts[1] : "Movie";

        String url = host + "/emby/Items?Ids=" + id + "&Fields=Overview,Genres&api_key=" + staticApiKey;
        String jsonStr = OkHttp.string(url, getHeaders());
        if (TextUtils.isEmpty(jsonStr)) return Result.get().string();
        
        JSONObject response = new JSONObject(jsonStr);
        JSONArray items = response.optJSONArray("Items");
        if (items == null || items.length() == 0) return Result.get().string();

        JSONObject item = items.getJSONObject(0);
        Vod vod = new Vod();
        vod.setVodId(rawId);
        vod.setVodName(item.optString("Name"));
        vod.setVodPic(host + "/emby/Items/" + id + "/Images/Primary?api_key=" + staticApiKey);
        vod.setVodContent(item.optString("Overview"));

        JSONArray genres = item.optJSONArray("Genres");
        if (genres != null) {
            List<String> genreList = new ArrayList<>();
            for (int j = 0; j < genres.length(); j++) {
                genreList.add(genres.getString(j));
            }
            vod.setTypeName(TextUtils.join(" / ", genreList));
        }

        vod.setVodPlayFrom("Emby私服");

        if ("Series".equals(type)) {
            String epUrl = host + "/emby/Shows/" + id + "/Episodes?api_key=" + staticApiKey;
            String epJsonStr = OkHttp.string(epUrl, getHeaders());
            if (!TextUtils.isEmpty(epJsonStr)) {
                JSONObject epResponse = new JSONObject(epJsonStr);
                JSONArray episodes = epResponse.optJSONArray("Items");

                List<String> playUrls = new ArrayList<>();
                if (episodes != null) {
                    for (int j = 0; j < episodes.length(); j++) {
                        JSONObject episode = episodes.getJSONObject(j);
                        String epId = episode.optString("Id");
                        int seasonIndex = episode.optInt("ParentIndexNumber", 1);
                        int episodeIndex = episode.optInt("IndexNumber", 1);
                        String epName = "S" + String.format("%02d", seasonIndex) + "E" + String.format("%02d", episodeIndex) + " " + episode.optString("Name");
                        playUrls.add(epName + "$" + epId);
                    }
                }
                vod.setVodPlayUrl(TextUtils.join("#", playUrls));
            }
        } else {
            vod.setVodPlayUrl("正片$" + id);
        }

        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String realUrl = "";
        try {
            String pbUrl = host + "/emby/Items/" + id + "/PlaybackInfo?api_key=" + staticApiKey 
                    + "&UserId=" + staticUserId + "&DeviceId=" + staticDeviceId;
            JSONObject reqBody = new JSONObject();
            reqBody.put("EnableDirectPlay", true);
            reqBody.put("EnableDirectStream", true);
            reqBody.put("EnableTranscoding", false); // 封杀转码行为

            JSONObject deviceProfile = new JSONObject();
            JSONArray directPlayProfiles = new JSONArray();
            JSONObject videoProfile = new JSONObject();

            videoProfile.put("Container", "mp4,mkv,m4v,mov,avi,flv,ts,m3u8,webm");
            videoProfile.put("Type", "Video");
            directPlayProfiles.put(videoProfile);
            deviceProfile.put("DirectPlayProfiles", directPlayProfiles);
            reqBody.put("DeviceProfile", deviceProfile);

            String pbRes = OkHttp.post(pbUrl, reqBody.toString(), getHeaders()).getBody();
            if (!TextUtils.isEmpty(pbRes)) {
                JSONObject pbJson = new JSONObject(pbRes);
                JSONArray mediaSources = pbJson.optJSONArray("MediaSources");
                if (mediaSources != null && mediaSources.length() > 0) {
                    JSONObject source = mediaSources.getJSONObject(0);
                    String directUrl = source.optString("DirectStreamUrl");
                    if (!TextUtils.isEmpty(directUrl)) {
                        realUrl = directUrl.startsWith("/") ? host + directUrl : directUrl;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (TextUtils.isEmpty(realUrl)) {
            realUrl = host + "/videos/" + id + "/original.mkv?api_key=" + staticApiKey;
        }

        try {
            String redirectUrl = OkHttp.getLocation(realUrl, new HashMap<String, String>());
            if (!TextUtils.isEmpty(redirectUrl)) {
                realUrl = redirectUrl;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Result.get().url(realUrl).string();
    }

    @Override
    public String searchContent(String keyword, boolean quick) throws Exception {
        String url = host + "/emby/Items?SearchTerm=" + Uri.encode(keyword) + "&IncludeItemTypes=Movie,Series&Recursive=true&Fields=PrimaryImageAspectRatio&api_key=" + staticApiKey;
        String jsonStr = OkHttp.string(url, getHeaders());
        if (TextUtils.isEmpty(jsonStr)) return Result.get().string();
        
        JSONObject response = new JSONObject(jsonStr);
        JSONArray items = response.optJSONArray("Items");

        List<Vod> list = new ArrayList<>();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String type = item.optString("Type");
                Vod vod = new Vod();
                vod.setVodId(item.optString("Id") + "_" + type);
                vod.setVodName(item.optString("Name"));
                vod.setVodPic(host + "/emby/Items/" + item.optString("Id") + "/Images/Primary?api_key=" + staticApiKey);
                vod.setVodRemarks("Series".equals(type) ? "剧集" : "电影");
                list.add(vod);
            }
        }
        return Result.get().vod(list).string();
    }
}
