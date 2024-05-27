package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;

import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Czsapp extends Spider {
    protected String siteurl = "https://www.czys.top/";
    private static final Pattern encryption = Pattern.compile(
            "\"([^\"]+)\";var [\\d\\w]+=function dncry.*md5.enc.Utf8.parse\\(\"([\\d\\w]+)\".*md5.enc.Utf8.parse\\(([\\d]+)\\)");
    private static final Pattern video = Pattern.compile("video: \\{url: \"([^\"]+)\"");
    private static final Pattern movie = Pattern.compile("/movie/(\\d+).html");
    private static final Pattern paging = Pattern.compile("/page/(\\d+)");
    private static final Pattern playr = Pattern.compile("/v_play/(.*)\\.html");

    @Override
    public void init(Context context) throws Exception {
        super.init(context);
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        super.init(context, extend);
        if (TextUtils.isEmpty(extend)) {
            return;
        }
        this.siteurl = extend;
    }

    protected static HashMap<String, String> getHeaders() {
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.75 Safari/537.36");
        return hashMap;
    }

    public String homeContent(boolean z) throws Exception {
        List<Vod> list = new ArrayList<>();
        List<Class> classes = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteurl, getHeaders()));
        Elements menus = doc.select(".submenu_mi > li > a");
        for (Element menu : menus) {
            String menuurl = menu.attr("href");
            if (menuurl.length() > 1) {
                classes.add(new Class(menuurl.substring(1), menu.text().trim()));
            }
        }
        Elements jS2 = doc.select("div.mi_ne_kd > ul > li");
        for (Element next2 : jS2) {
            Matcher matcher = movie.matcher(next2.select("a").attr("href"));
            if (matcher.find()) {
                String id = matcher.group(1);
                String name = next2.select("img").attr("alt").trim();
                String pic = next2.select("img").attr("data-original").trim();
                String remarks = next2.select("div.hdinfo > span").text().trim();
                list.add(new Vod(id, name, pic, remarks));
            }
        }
        return Result.string(list);
    }

    public String categoryContent(String str, String str2, boolean z, HashMap<String, String> hashMap)
            throws Exception {
        List<Vod> list = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteurl + "/" + str + "/page/" + str2, getHeaders()));
        SpiderDebug.log(siteurl + "/" + str + "/page/");
        int page = Integer.parseInt(str2);
        int pagecount = Integer.parseInt(str2);
        Matcher matcher = paging.matcher(doc.select("div.pagenavi_txt > a.extend").last().attr("href"));
        if (matcher.find()) {
            pagecount = Integer.parseInt(matcher.group(1));
        }
        Elements jS = doc.select("div.mi_ne_kd > ul > li");
        for (Element next : jS) {
            Matcher matcher2 = movie.matcher(next.select("a").attr("href"));
            if (matcher2.find()) {
                String id = matcher2.group(1);
                String name = next.select("img").attr("alt").trim();
                String pic = next.select("img").attr("data-original").trim();
                String remarks = next.select("div.hdinfo > span").text().trim();
                list.add(new Vod(id, name, pic, remarks));
            }
        }
        return Result.string(page, pagecount, list.size(), page <= 1 ? list.size() : pagecount * list.size(), list);
    }

    public String detailContent(List<String> list) throws Exception {
        List<Vod> vods = new ArrayList<>();
        Document doc = Jsoup.parse(OkHttp.string(siteurl + "/movie/" + list.get(0) + ".html", getHeaders()));
        String name = doc.select("div.moviedteail_tt > h1").text().trim();
        String pic = doc.select("div.dyimg > img").attr("src");
        String content = doc.select("div.yp_context").text().trim();
        Vod vod = new Vod(list.get(0), name, pic, content);
        Iterator<Element> blurb = doc.select("ul.moviedteail_list > li").iterator();
        while (blurb.hasNext()) {
            String[] next = blurb.next().text().split("：");
            if (next.length <= 0)
                continue;
            if (next[0].equals("类型"))
                vod.setTypeName(next[1]);
            if (next[0].equals("地区"))
                vod.setVodArea(next[1]);
            if (next[0].equals("年份"))
                vod.setVodYear(next[1]);
            if (next[0].equals("导演"))
                vod.setVodDirector(next[1]);
            if (next[0].equals("主演"))
                vod.setVodActor(next[1]);

        }
        ArrayList plays = new ArrayList<>();
        for (Element play : doc.select("div.paly_list_btn > a")) {
            Matcher playurl = playr.matcher(play.attr("href"));
            if (playurl.find()) {
                plays.add(String.join("$", play.text(), playurl.group(1)));
            }
            vod.setVodRemarks(play.text());
        }
        vod.setVodPlayUrl(TextUtils.join("#", plays));
        vod.setVodPlayFrom("厂长");
        vods.add(vod);
        return Result.string(vods);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        String playUrl = new String();

        String html = OkHttp.string(siteurl + "/v_play/" + id + ".html", getHeaders());
        Document doc = Jsoup.parse(html);
        Matcher matcher = encryption.matcher(html);
        if (matcher.find()) {
            String group = matcher.group(1);
            String KEY = matcher.group(2);
            String IV = matcher.group(3);
            String decode = AESCBC(group, KEY, IV);
            Matcher videomatch = video.matcher(decode);
            playUrl = videomatch.find() ? videomatch.group(1) : "";
        }
        if (playUrl.isEmpty())
            playUrl = doc.select("div.videoplay >iframe").attr("src");

        return Result.get().url(playUrl).header(getHeaders()).string();
    }

    private static String AESCBC(String src, String KEY, String IV) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
            AlgorithmParameterSpec paramSpec = new IvParameterSpec(IV.getBytes());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
            return new String(cipher.doFinal(Base64.decode(src.getBytes(), 0)));
        } catch (Exception exception) {
            SpiderDebug.log(exception);
        }
        return null;
    }
}