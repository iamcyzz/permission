package com.mmall.filter;

import com.google.common.base.Splitter;
import com.google.common.collect.Sets;
import com.mmall.common.ApplicationContextHelper;
import com.mmall.common.JsonData;
import com.mmall.common.RequestHolder;
import com.mmall.model.SysUser;
import com.mmall.service.SysCoreService;
import com.mmall.util.JsonMapper;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class AclControlFilter implements Filter {

    private static Set<String> exclusionUrlSet = Sets.newConcurrentHashSet();

    private final static String noAuthUrl = "/sys/user/noAuth.page";


    @Override
    //定义一个不拦截的白名单
    public void init(FilterConfig filterConfig) throws ServletException {
        //getInitParameter就是获取到我们过滤器一开始配置的参数，配置在了过滤原始参数里
        String exclusionUrls = filterConfig.getInitParameter("exclusionUrls");
        //以逗号 ，分割，去掉空格和空字符串，转为list
        List<String> exclusionUrlList = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(exclusionUrls);
        //放到set集合里面
        exclusionUrlSet = Sets.newConcurrentHashSet(exclusionUrlList);
        //这个错误页面也是不需要权限就能访问的，也放到不拦截的白名单里面
        exclusionUrlSet.add(noAuthUrl);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        //转为HttpServletRequest类型
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        //获取访问地址
        String servletPath = request.getServletPath();
        //获取访问参数
        Map requestMap = request.getParameterMap();

        //假如访问的名单在白名单内里，直接不拦截，直接跳过，让他访问
        if (exclusionUrlSet.contains(servletPath)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        //获取用户,用户为空说明没有登陆，直接返回没有权限访问页面
        SysUser sysUser = RequestHolder.getCurrentUser();
        if (sysUser == null) {
            log.info("someone visit {}, but no login, parameter:{}", servletPath, JsonMapper.obj2String(requestMap));
            noAuth(request, response);
            return;
        }

        //我们这个过滤器不是被spring管理的，所以我们要通过我们的工具类，来拿到这个bean，而不能直接通过注入拿到
        SysCoreService sysCoreService = ApplicationContextHelper.popBean(SysCoreService.class);
        //调用权限判断方法，假如没有权限就返回false自然进入里面的操作，返回没有权限的界面

        if (!sysCoreService.hasUrlAcl(servletPath)) {
            log.info("{} visit {}, but no login, parameter:{}", JsonMapper.obj2String(sysUser), servletPath, JsonMapper.obj2String(requestMap));
            noAuth(request, response);
            return;
        }

        //上面的权限都通过了，那么直接正常返回正常页面的跳转，不拦截
        filterChain.doFilter(servletRequest, servletResponse);
        return;
    }

    //返回无权限访问页面的方法，上面调用这个方法来进行跳转
    //首先要判断是jason请求还是页面请求
    private void noAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String servletPath = request.getServletPath();
        //假如是json请求，那么就返回json提示信息
        if (servletPath.endsWith(".json")) {
            JsonData jsonData = JsonData.fail("没有访问权限，如需要访问，请联系管理员");
            response.setHeader("Content-Type", "application/json");
            response.getWriter().print(JsonMapper.obj2String(jsonData));
            return;
        } else {
            //否则就定向跳转到无权限页面，直接把无权限页面的路径给他传入
            clientRedirect(noAuthUrl, response);
            return;
        }
    }

    //然后继续写跳转逻辑
    //然后他会跳转到传入的无权限页面里面，ret携带的是我们之前访问页面的值
    private void clientRedirect(String url, HttpServletResponse response) throws IOException{
        response.setHeader("Content-Type", "text/html");
        response.getWriter().print("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" + "<head>\n" + "<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\"/>\n"
                + "<title>跳转中...</title>\n" + "</head>\n" + "<body>\n" + "跳转中，请稍候...\n" + "<script type=\"text/javascript\">//<![CDATA[\n"
                + "window.location.href='" + url + "?ret='+encodeURIComponent(window.location.href);\n" + "//]]></script>\n" + "</body>\n" + "</html>\n");
    }

    @Override
    public void destroy() {

    }
}
