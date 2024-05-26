package com.catvod.spider;

import com.github.catvod.spider.TvYB;
import org.junit.Test;



public class TvYBTest {
    
    @Test 
    public void homeContent() {
       TvYB tv = new TvYB();
       try {
        String jsonstr = tv.homeContent(false);
        System.out.println(jsonstr);

       } catch (Exception e) {
        // TODO: handle exception
       }
        
    }
}
