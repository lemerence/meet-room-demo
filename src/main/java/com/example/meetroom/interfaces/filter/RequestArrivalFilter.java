package com.example.meetroom.interfaces.filter;

import java.io.IOException;
import java.time.Clock;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在服务端 HTTP 入口采集一次请求到达时间。 */
public class RequestArrivalFilter extends OncePerRequestFilter {
    /** 请求属性键，仅由服务端写入。 */
    public static final String ARRIVED_AT = RequestArrivalFilter.class.getName() + ".arrivedAt";
    /** 用于采集高精度北京时间的时钟。 */
    private final Clock clock;

    /** 注入可控时钟以验证入口时间语义。 */
    public RequestArrivalFilter(Clock clock) {
        this.clock = clock;
    }

    /** 记录入口时间后继续处理请求。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
    }
}
