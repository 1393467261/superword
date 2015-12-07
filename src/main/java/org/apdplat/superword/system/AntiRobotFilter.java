/*
 * APDPlat - Application Product Development Platform
 * Copyright (c) 2013, 杨尚川, yang-shangchuan@qq.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.apdplat.superword.system;

import org.apache.commons.lang.StringUtils;
import org.apdplat.superword.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 反爬虫反机器人攻击
 * Created by ysc on 12/4/15.
 */
public class AntiRobotFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(AntiRobotFilter.class);

    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    public static volatile int limit = 1000;
    public static volatile int invalidCount = 0;

    private static ServletContext servletContext = null;

    public void destroy() {
    }

    private String getKey(HttpServletRequest request){
        String ip = request.getRemoteAddr();
        User user = (User) request.getSession().getAttribute("user");
        String userString = user==null?"anonymity":user.getUserName();
        return "anti-robot-"+userString+"-"+ip+"-"+SIMPLE_DATE_FORMAT.format(new Date());
    }

    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest)req;
        String userAgent = request.getHeader("User-Agent");
        if(StringUtils.isBlank(userAgent)){
            invalidCount++;
            HttpServletResponse response = (HttpServletResponse)resp;
            response.setContentType("text/html");
            response.setCharacterEncoding("utf-8");
            response.getWriter().write("superword是一个Java实现的英文单词分析和辅助阅读开源项目，主要研究英语单词音近形似转化规律、前缀后缀规律、词之间的相似性规律和辅助阅读等等。Clean code、Fluent style、Java8 feature: Lambdas, Streams and Functional-style Programming。 升学考试、工作求职、充电提高，都少不了英语的身影，英语对我们来说实在太重要了。你还在为记不住英语单词而苦恼吗？还在为看不懂英文资料和原版书籍而伤神吗？superword可以在你英语学习的路上助你一臂之力。 superword利用计算机强大的计算能力，使用机器学习和数据挖掘算法找到读音相近、外形相似、含义相关、同义反义、词根词缀的英语单词，从而非常有利于我们深入地记忆理解这些单词，同时，辅助阅读功能更是能够提供阅读的速度和质量。 支持最权威的2部中文词典和9部英文词典，支持23种分级词汇，囊括了所有的英语考试，还专门针对程序员提供了249本最热门的技术书籍的辅助阅读功能。");
            return;
        }

        if(servletContext == null){
            servletContext = request.getServletContext();
        }
        String key = getKey(request);
        AtomicInteger count = (AtomicInteger)servletContext.getAttribute(key);
        if(count == null){
            count = new AtomicInteger();
            servletContext.setAttribute(key, count);
        }

        if(count.incrementAndGet() > limit){
            HttpServletResponse response = (HttpServletResponse)resp;
            response.setContentType("text/html");
            response.setCharacterEncoding("utf-8");
            response.getWriter().write("系统检测到您所在的IP访问过于频繁，给您造成的不便敬请谅解，请明天再来。再见！");

            return;
        }

        chain.doFilter(req, resp);
    }

    public void init(FilterConfig config) throws ServletException {
        int initialDelay = 24-LocalDateTime.now().getHour();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                LOG.info("clear last day anti-robot counter");
                LocalDateTime timePoint = LocalDateTime.now().minusDays(1);
                String date = SIMPLE_DATE_FORMAT.format(Date.from(timePoint.atZone(ZoneId.systemDefault()).toInstant()));
                List<String> archive = new ArrayList<>();
                Enumeration<String> keys = servletContext.getAttributeNames();
                while (keys.hasMoreElements()) {
                    String key = keys.nextElement();
                    if (key.startsWith("anti-robot-") && key.endsWith(date)) {
                        archive.add(key);
                    }
                }
                archive.forEach(servletContext::removeAttribute);
                File path = new File(servletContext.getRealPath("/WEB-INF/data/anti-robot-archive/"));
                if (!path.exists()) {
                    path.mkdirs();
                }
                archive.add("user agent invalid count: "+invalidCount);
                invalidCount = 0;
                String file = path.getPath() + date + ".txt";
                Files.write(Paths.get(file), archive);
                LOG.info("clear last day anti-robot counter finished: " + file);
            } catch (Exception e) {
                LOG.error("save anti-robot-archive failed", e);
            }
        }, initialDelay, 24, TimeUnit.HOURS);
    }

    public static List<String> getData(){
        Map<String, Integer> map = new HashMap<>();
        Enumeration<String> keys = servletContext.getAttributeNames();
        while(keys.hasMoreElements()){
            String key = keys.nextElement();
            if(key.startsWith("anti-robot-")){
                map.put(key.substring(11), ((AtomicInteger) servletContext.getAttribute(key)).intValue());
            }
        }
        return map
                .entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> e.getKey() + "-" + e.getValue())
                .collect(Collectors.toList());
    }

    public static void main(String[] args) {
        LocalDateTime timePoint = LocalDateTime.now().minusDays(1);
        String date = SIMPLE_DATE_FORMAT.format(Date.from(timePoint.atZone(ZoneId.systemDefault()).toInstant()));
        System.out.println(date);
    }
}
