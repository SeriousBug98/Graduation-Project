package com.example.dbids.it;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class IT07_DashboardEventsIT extends ItBase {

    @Test @DisplayName("IT-07: Admin이 /api/events 조회 (5xx 없음)")
    void dashboard_events_page() throws Exception {
        var res = mvc.perform(get("/api/events").param("page","0").param("size","10"))
                .andReturn().getResponse();
        expectNot5xx(res.getStatus());
    }
}
