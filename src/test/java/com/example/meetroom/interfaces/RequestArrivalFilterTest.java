package com.example.meetroom.interfaces;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.example.meetroom.interfaces.filter.RequestArrivalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;

/** 验证请求时间来自服务端，保留精度且不会因后续处理改写。 */
class RequestArrivalFilterTest {
    /** 验证请求头不能伪造时间，采集发生在后续请求处理之前。 */
    @Test
    void capturesServerTimeBeforeChainWithFullPrecision() throws Exception {
        var clock = Clock.fixed(Instant.parse("2030-01-01T01:59:59.123456789Z"), ZoneId.of("Asia/Shanghai"));
        var request = new MockHttpServletRequest();
        request.addHeader("X-Request-Arrived-At", "2099-01-01 00:00");
        new RequestArrivalFilter(clock).doFilter(request, new MockHttpServletResponse(), (req, res) ->
                assertThat(req.getAttribute(RequestArrivalFilter.ARRIVED_AT))
                        .isEqualTo(LocalDateTime.parse("2030-01-01T09:59:59.123456789")));
    }

    /** 验证嵌套过滤器分派复用首次采集值，不刷新时间。 */
    @Test
    void nestedDispatchKeepsOriginalArrival() throws Exception {
        var first = new RequestArrivalFilter(Clock.fixed(Instant.parse("2030-01-01T01:00:00Z"), ZoneId.of("Asia/Shanghai")));
        var later = new RequestArrivalFilter(Clock.fixed(Instant.parse("2030-01-01T03:00:00Z"), ZoneId.of("Asia/Shanghai")));
        var request = new MockHttpServletRequest();
        first.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                later.doFilter(req, res, (nested, response) ->
                        assertThat(nested.getAttribute(RequestArrivalFilter.ARRIVED_AT))
                                .isEqualTo(LocalDateTime.parse("2030-01-01T09:00:00"))));
    }
}
