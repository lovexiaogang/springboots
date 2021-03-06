package com.study.www.service;

import com.study.www.entity.PipiInfo;
import com.study.www.mapper.PipiInfoSimpleMapper;
import com.study.www.utils.PipiUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Selectable;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * DzspProcessorService: 大宗商品刷选下载器
 *
 * @auther : Administrator.zhuyanpeng
 * @date : 2017/7/25    17:40
 **/
@Component
@PropertySource("classpath:application.properties")
public class DzspProcessorService implements PageProcessor{
    private Logger logger=Logger.getLogger(this.getClass());
    @Value("${dzsp.resultDay}")
    private List<String> resultDay;
    @Value("${dzsp.url}")
    private String url;
    @Autowired
    private PipiInfoSimpleMapper pipiInfoSimpleMapper;

    private void setResultDay(List<String> resultDay) {
        if (StringUtils.isNotBlank(resultDay.get(0).split(";")[0])) {
            if(resultDay.size()==1){
                String[] split = resultDay.get(0).split(";");
                resultDay= new ArrayList<>();
                for (String s : split) {
                    if(StringUtils.isNotBlank(s.trim())){
                        resultDay.add(s.trim());
                    }
                }
            }
        }else{
            Calendar cal = Calendar.getInstance();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            Date date = new Date();
            cal.setTime(date);
            int w = cal.get(Calendar.DAY_OF_WEEK) - 1;
            if (w ==1){
                cal.add(Calendar.DATE, -3);//当前时间前去一个天，即一个月前的时间
            }else{
                cal.add(Calendar.DATE, -1);
            }
            Date time = cal.getTime();
            String format = simpleDateFormat.format(time);
            resultDay = new ArrayList<>();
            resultDay.add(format);
        }
        this.resultDay = resultDay;
    }
    //网站需求
    private Site site=Site.me()
            .setUserAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/536.27.1 (KHTML, like Gecko) Version/5.1.2 Safari/534.52.7")
            .setRetrySleepTime(2)
            .setSleepTime(2000)
            .setRetryTimes(10)
            .setCharset("utf-8");

    @Override
    public void process(Page page) {
        //配置分析
       setResultDay(resultDay);
       //是否有数据有的话直接杀死
       if(isExistData(resultDay)){
            logger.warn("数据库中已存在！系统关闭!");
           System.exit(0);
       }
        List<String> all =new ArrayList<>();
        for (String s : page.getHtml().links().regex("(list-1+\\w{1}+-1.html)").all()) {
            all.add(url+s);
        }
        page.addTargetRequests(all);
        //一级目录 获得所有"更多"
        if (page.getUrl().regex(url+"(list-1+\\w{1}+-1.html)").match()){
            //下级目录
            all.clear();
            for (String s : page.getHtml().xpath("//html/body/div[8]/div[2]/div/div[@class='p-cate-a']/div[2]/ul/li/a/@href").all()) {
                all.add(url+s);
            }
            page.addTargetRequests(all);
        }
        //二级目录展现所有单品
        if (regexSingleGoods(url,page.getUrl().get())){
            String  className= page.getHtml().xpath("/html/body/div[8]/div[1]/a[4]/span/text()").toString();
            Selectable selectable = page.getHtml().xpath("/html/body/div[8]/div[5]/div[1]/table[@class='lp-table mb15']/").xpath("/tbody/");
            List<PipiInfo> pipiInfos = PipiUtils.getPiPiInfosBySelectable(selectable,className,resultDay);
            page.putField("pipiInfos",pipiInfos);
        }
    }

    private boolean isExistData(List<String> resultDay) {
        for (String time : resultDay) {
            int count = pipiInfoSimpleMapper.queryCountByTime(time);
            if (count<10){
                return false;
            }
        }
        return true;
    }

    //二级单品url抉择
    private static boolean regexSingleGoods(String url,String regexUrl){
        int l = regexUrl.indexOf(url + "plist-")==0?(url+"plist-").length():-1;
        int r = regexUrl.indexOf("-1.html");
        if (l>0&&r>0){
            String str =  regexUrl.substring(l,r);
            return Integer.valueOf(str)>19;
        }
        return false;

    }

    @Override
    public Site getSite() {
        return site;
    }

    public void start(DzspProcessorService processor, PipiInfoline pipiInfoline) {
        Spider.create(processor)
                .addUrl(url)
                .addPipeline(pipiInfoline)
                .thread(5)
                .run();
    }
}
