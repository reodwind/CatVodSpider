package com.github.catvod.spider;

import android.content.Context;
import android.net.Uri;
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

public class Emby extends Spider {
    private String host;
    private String apiKey;

    @Override
    public void init(Context context, String extend) throws Exception {
        if (extend.startsWith("http")) {
            extend = OkHttp.string(extend);
        }
        JSONObject json = new JSONObject(extend);
        host = json.optString("url");
        apiKey = json.optString("token");
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        String url = host + "/emby/Library/VirtualFolders?api_key=" + apiKey;
        String jsonStr = OkHttp.string(url);
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
                + "&Fields=PrimaryImageAspectRatio,Overview&Recursive=true&IncludeItemTypes=Movie,Series&api_key=" + apiKey;
        
        String jsonStr = OkHttp.string(url);
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
                vod.setVodPic(host + "/emby/Items/" + item.optString("Id") + "/Images/Primary?api_key=" + apiKey);
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

        String url = host + "/emby/Items?Ids=" + id + "&Fields=Overview,Genres&api_key=" + apiKey;
        String jsonStr = OkHttp.string(url);
        if (TextUtils.isEmpty(jsonStr)) return Result.get().string();
        
        JSONObject response = new JSONObject(jsonStr);
        JSONArray items = response.optJSONArray("Items");
        if (items == null || items.length() == 0) return Result.get().string();

        JSONObject item = items.getJSONObject(0);
        Vod vod = new Vod();
        vod.setVodId(rawId);
        vod.setVodName(item.optString("Name"));
        vod.setVodPic(host + "/emby/Items/" + id + "/Images/Primary?api_key=" + apiKey);
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
            String epUrl = host + "/emby/Shows/" + id + "/Episodes?api_key=" + apiKey;
            String epJsonStr = OkHttp.string(epUrl);
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
            String pbUrl = host + "/emby/Items/" + id + "/PlaybackInfo?api_key=" + apiKey;
            JSONObject reqBody = new JSONObject();
            reqBody.put("EnableDirectPlay", true);
            reqBody.put("EnableDirectStream", true);
            reqBody.put("EnableTranscoding", false); // 封杀转码行为
            
            String pbRes = OkHttp.post(pbUrl, reqBody.toString());
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
            realUrl = host + "/videos/" + id + "/original.mkv?api_key=" + apiKey;
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
        String url = host + "/emby/Items?SearchTerm=" + Uri.encode(keyword) + "&IncludeItemTypes=Movie,Series&Recursive=true&Fields=PrimaryImageAspectRatio&api_key=" + apiKey;
        String jsonStr = OkHttp.string(url);
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
                vod.setVodPic(host + "/emby/Items/" + item.optString("Id") + "/Images/Primary?api_key=" + apiKey);
                vod.setVodRemarks("Series".equals(type) ? "剧集" : "电影");
                list.add(vod);
            }
        }
        return Result.get().vod(list).string();
    }
}
