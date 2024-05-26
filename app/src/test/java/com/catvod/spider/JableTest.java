package com.catvod.spider;

import com.github.catvod.spider.Jable;
import org.junit.Test;


public class JableTest {
    @Test 
    public void homeContent() {
        Jable tv = new Jable();
       try {
        String jsonstr = tv.homeContent(false);
        System.out.println(jsonstr);

       } catch (Exception e) {
        // TODO: handle exception
       }
        
    }
}
