package edu.szu.moments.auth;

import com.fasterxml.jackson.databind.JsonNode;
import edu.szu.moments.posts.UserActivity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"moments.auth.requests-per-second=1000","moments.auth.burst=1000"})
class SocialStatisticsIntegrationTest {
    record User(long id, String account, String token) {}
    private static final String PASSWORD="Social-test-2026";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    @Autowired FriendService friends;
    @Autowired FriendRelations relations;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void friendLifecycleSearchPaginationAndReapplication() {
        var a=user(); var b=user();
        var found=ok(call(HttpMethod.GET,"/users/search?keyword="+b.account(),a,null),200);
        assertThat(found.path("items").get(0).path("id").asLong()).isEqualTo(b.id());
        assertThat(found.path("items").get(0).has("passwordHash")).isFalse();
        long id=send(a,b);
        assertThat(ok(call(HttpMethod.GET,"/users/search?keyword="+b.account(),a,null),200)
                .path("items").get(0).path("relationship").asText()).isEqualTo("PENDING_SENT");
        assertThat(ok(call(HttpMethod.GET,"/friend-requests",b,null),200).path("total").asLong()).isEqualTo(1);
        assertThat(ok(call(HttpMethod.GET,"/friend-requests?direction=outgoing",a,null),200).path("total").asLong()).isEqualTo(1);
        ok(call(HttpMethod.POST,"/friend-requests",b,Map.of("userId",a.id())),409);
        ok(call(HttpMethod.PUT,"/friend-requests/"+id,a,Map.of("action","ACCEPT")),403);
        assertThat(ok(call(HttpMethod.PUT,"/friend-requests/"+id,b,Map.of("action","ACCEPT")),200).path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(relations.areFriends(a.id(),b.id())).isTrue();
        assertThat(ok(call(HttpMethod.GET,"/friends",a,null),200).path("items").get(0).path("id").asLong()).isEqualTo(b.id());
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId",b.id())),409);
        ok(call(HttpMethod.PUT,"/friend-requests/"+id,b,Map.of("action","ACCEPT")),409);
        ok(call(HttpMethod.DELETE,"/friends/"+b.id(),a,null),200);
        ok(call(HttpMethod.DELETE,"/friends/"+b.id(),a,null),200);
        assertThat(relations.areFriends(a.id(),b.id())).isFalse();
        long next=send(a,b);
        assertThat(next).isNotEqualTo(id);
        ok(call(HttpMethod.PUT,"/friend-requests/"+id,b,Map.of("action","ACCEPT")),409);
        ok(call(HttpMethod.PUT,"/friend-requests/"+next,b,Map.of("action","REJECT")),200);
        assertThat(send(b,a)).isGreaterThan(next);
    }

    @Test
    void friendPermissionsValidationAndDisabledCleanup() {
        var a=user(); var b=user(); var c=user(); var admin=admin();
        ok(call(HttpMethod.GET,"/friends",null,null),401);
        ok(call(HttpMethod.GET,"/friends",admin,null),403);
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId",a.id())),400);
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of()),400);
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId",1.5)),400);
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId","123")),400);
        ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId",admin.id())),404);
        ok(call(HttpMethod.GET,"/users/search?keyword=",a,null),400);
        ok(call(HttpMethod.GET,"/friends?pageSize=51",a,null),400);
        ok(call(HttpMethod.GET,"/friend-requests?direction=all",a,null),400);
        long accepted=send(a,b);
        ok(call(HttpMethod.PUT,"/friend-requests/"+accepted,c,Map.of("action","ACCEPT")),403);
        ok(call(HttpMethod.PUT,"/friend-requests/"+accepted,b,Map.of("action","UNKNOWN")),400);
        ok(call(HttpMethod.PUT,"/friend-requests/"+accepted,b,Map.of("action","ACCEPT")),200);
        long pending=send(c,b);
        ok(call(HttpMethod.DELETE,"/admin/users/"+b.id(),admin,null),200);
        assertThat(relations.areFriends(a.id(),b.id())).isFalse();
        assertThat(db.queryForObject("SELECT status FROM friend_requests WHERE id=?",String.class,pending)).isEqualTo("CANCELLED");
        assertThat(ok(call(HttpMethod.GET,"/friends",a,null),200).path("total").asLong()).isZero();
        assertThat(ok(call(HttpMethod.GET,"/users/search?keyword="+b.account(),a,null),200).path("total").asLong()).isZero();
        ok(call(HttpMethod.GET,"/friends",b,null),401);
        ok(call(HttpMethod.POST,"/friend-requests",c,Map.of("userId",b.id())),404);
    }

    @Test
    void reciprocalConcurrentRequestsProduceExactlyOnePending() throws Exception {
        var a=user(); var b=user();
        var start=new CountDownLatch(1); var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=new ArrayList<Future<Integer>>();
            for (var pair:List.of(List.of(a,b),List.of(b,a))) jobs.add(pool.submit(()->{
                ready.countDown(); start.await();
                try { friends.send(profile(pair.get(0)),0,pair.get(1).id()); return 201; }
                catch(edu.szu.moments.api.ApiFailure e) { return e.status().value(); }
            }));
            try { assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); } finally { start.countDown(); }
            assertThat(List.of(jobs.get(0).get(10,TimeUnit.SECONDS),jobs.get(1).get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201,409);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM friend_requests WHERE user_low=? AND user_high=? AND status='PENDING'",
                Long.class,Math.min(a.id(),b.id()),Math.max(a.id(),b.id()))).isEqualTo(1);
    }

    @Test
    void concurrentDecisionsAndStaleTokenCannotMutateRelationships() throws Exception {
        var a=user(); var b=user(); long id=send(a,b);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var jobs=new ArrayList<Future<Integer>>();
            for(String action:List.of("ACCEPT","REJECT")) jobs.add(pool.submit(()->{
                start.await();
                try { friends.decide(profile(b),0,id,action); return 200; }
                catch(edu.szu.moments.api.ApiFailure e) { return e.status().value(); }
            }));
            start.countDown();
            assertThat(List.of(jobs.get(0).get(10,TimeUnit.SECONDS),jobs.get(1).get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        var c=user();
        db.update("UPDATE users SET token_version=1 WHERE id=?",c.id());
        assertThatThrownBy(()->friends.send(profile(c),0,a.id())).isInstanceOfSatisfying(
                edu.szu.moments.api.ApiFailure.class,e->assertThat(e.status()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM friend_requests WHERE requester_id=?",Long.class,c.id())).isZero();
    }

    @Test
    void successfulBusinessActionsRecordOnceAndFailuresDoNotCount() {
        var a=user(); var b=user(); var admin=admin();
        // Remove setup login records to prove each business integration actually records activity.
        clearActivity(a,b);
        ok(call(HttpMethod.POST,"/posts",a,Map.of("text","业务活跃测试","mediaIds",List.of(),"tags",List.of())),200);
        assertActivity(a,1);
        long post=db.queryForObject("SELECT MAX(id) FROM posts WHERE author_id=?",Long.class,a.id());
        clearActivity(a);
        ok(call(HttpMethod.PATCH,"/posts/"+post,a,Map.of("text","修改正文")),200); assertActivity(a,1);
        clearActivity(a,b);
        ok(call(HttpMethod.PUT,"/posts/"+post+"/rating",b,Map.of("score",4)),200); assertActivity(b,1);
        clearActivity(b);
        ok(call(HttpMethod.POST,"/posts/"+post+"/comments",b,Map.of("content","真实评论")),200); assertActivity(b,1);
        clearActivity(a,b);
        ok(call(HttpMethod.POST,"/posts/"+post+"/comments",a,Map.of("content","作者评论")),403);
        ok(call(HttpMethod.PUT,"/posts/"+post+"/rating",b,Map.of("score",9)),400);
        ok(call(HttpMethod.GET,"/posts/"+post,b,null),200);
        assertActivity(a,0); assertActivity(b,0);
        login(a); login(a); assertActivity(a,1);
        ok(call(HttpMethod.POST,"/auth/login",null,Map.of("account",b.account(),"password","wrong-password")),401);
        assertActivity(b,0);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM daily_user_activity WHERE user_id=?",Long.class,admin.id())).isZero();
    }

    @Test
    void activityTimezoneDeduplicationAndRollback() {
        var a=user(); clearActivity(a);
        var tx=new TransactionTemplate(transactions);
        Instant boundary=Instant.parse("2026-11-26T16:00:00Z");
        tx.executeWithoutResult(s->{ events.publishEvent(new UserActivity(a.id(),boundary.minusSeconds(1)));
            events.publishEvent(new UserActivity(a.id(),boundary)); events.publishEvent(new UserActivity(a.id(),boundary.plusSeconds(5))); });
        assertThat(db.queryForList("SELECT activity_day FROM daily_user_activity WHERE user_id=? ORDER BY activity_day",java.sql.Date.class,a.id()))
                .containsExactly(java.sql.Date.valueOf("2026-11-26"),java.sql.Date.valueOf("2026-11-27"));
        tx.executeWithoutResult(s->{events.publishEvent(new UserActivity(a.id(),boundary.plusSeconds(86400))); s.setRollbackOnly();});
        assertThat(db.queryForObject("SELECT COUNT(*) FROM daily_user_activity WHERE user_id=?",Long.class,a.id())).isEqualTo(2);
    }

    @Test
    void statisticsPermissionsRealCountsAndDateBoundaries() {
        var a=user(); var admin=admin();
        ok(call(HttpMethod.GET,"/admin/statistics",null,null),401);
        ok(call(HttpMethod.GET,"/admin/statistics",a,null),403);
        ok(call(HttpMethod.GET,"/admin/statistics?from=2026-02-30",admin,null),400);
        ok(call(HttpMethod.GET,"/admin/statistics?from=2025-01-01&to=2026-11-27",admin,null),400);
        ok(call(HttpMethod.GET,"/admin/statistics?from=2026-11-27&to=2026-11-26",admin,null),400);
        ok(call(HttpMethod.GET,"/admin/statistics?to=2099-01-01",admin,null),400);
        var today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var body=ok(call(HttpMethod.GET,"/admin/statistics?from="+today+"&to="+today,admin,null),200);
        assertThat(body.path("daily").size()).isEqualTo(1);
        assertThat(body.path("timezone").asText()).isEqualTo("Asia/Shanghai");
        assertThat(body.path("totals").path("userCount").asLong()).isEqualTo(db.queryForObject("SELECT COUNT(*) FROM users WHERE role='USER'",Long.class));
        assertThat(body.path("daily").get(0).path("activeUsers").asLong()).isEqualTo(db.queryForObject(
                "SELECT COUNT(*) FROM daily_user_activity WHERE activity_day=?",Long.class,java.sql.Date.valueOf(today)));
        var before=Instant.parse(body.path("trackingStartedAt").asText()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().minusDays(1);
        assertThat(ok(call(HttpMethod.GET,"/admin/statistics?from="+before+"&to="+before,admin,null),200)
                .path("daily").get(0).path("activeUsers").isNull()).isTrue();
        assertThat(ok(call(HttpMethod.GET,"/admin/statistics",admin,null),200).path("daily").size()).isEqualTo(30);
    }

    @Test
    void statisticsCurrentTotalsFollowDeletionButDailyCreationsRemain() {
        var admin=admin(); var a=user(); var b=user();
        var baseline=ok(call(HttpMethod.GET,"/admin/statistics",admin,null),200);
        long post=ok(call(HttpMethod.POST,"/posts",a,Map.of("text","统计删除口径")),200).path("id").asLong();
        ok(call(HttpMethod.POST,"/posts/"+post+"/comments",b,Map.of("content","统计评论")),200);
        ok(call(HttpMethod.PUT,"/posts/"+post+"/rating",b,Map.of("score",4)),200);
        var after=ok(call(HttpMethod.GET,"/admin/statistics",admin,null),200);
        for(String field:List.of("postCount","commentCount","ratingCount"))
            assertThat(after.path("totals").path(field).asLong()).isEqualTo(baseline.path("totals").path(field).asLong()+1);
        long creations=after.path("daily").get(after.path("daily").size()-1).path("newPosts").asLong();
        ok(call(HttpMethod.DELETE,"/posts/"+post,a,null),200);
        var deleted=ok(call(HttpMethod.GET,"/admin/statistics",admin,null),200);
        for(String field:List.of("postCount","commentCount","ratingCount"))
            assertThat(deleted.path("totals").path(field)).isEqualTo(baseline.path("totals").path(field));
        assertThat(deleted.path("daily").get(deleted.path("daily").size()-1).path("newPosts").asLong()).isEqualTo(creations);
        ok(call(HttpMethod.DELETE,"/admin/users/"+a.id(),admin,null),200);
        assertThat(ok(call(HttpMethod.GET,"/admin/statistics",admin,null),200).path("totals").path("userCount"))
                .isEqualTo(baseline.path("totals").path("userCount"));
    }

    private User user() {
        String account="s_"+UUID.randomUUID().toString().replace("-","").substring(0,18);
        long id=ok(call(HttpMethod.POST,"/auth/register",null,Map.of("account",account,"password",PASSWORD,"nickname",account)),201).path("id").asLong();
        var u=new User(id,account,""); return new User(id,account,login(u));
    }
    private User admin() {
        String account="adm_"+UUID.randomUUID().toString().replace("-","").substring(0,18);
        auth.initializeAdmin(account,PASSWORD,"统计管理员");
        long id=db.queryForObject("SELECT id FROM users WHERE account=?",Long.class,account);
        return new User(id,account,login(new User(id,account,"")));
    }
    private String login(User user) { return ok(call(HttpMethod.POST,"/auth/login",null,Map.of("account",user.account(),"password",PASSWORD)),200).path("accessToken").asText(); }
    private long send(User a,User b) { return ok(call(HttpMethod.POST,"/friend-requests",a,Map.of("userId",b.id())),201).path("id").asLong(); }
    private UserProfile profile(User a) { return new UserProfile(a.id(),a.account(),a.account(),"USER"); }
    private void clearActivity(User... people) { for(var u:people) db.update("DELETE FROM daily_user_activity WHERE user_id=?",u.id()); }
    private void assertActivity(User a,long count) { assertThat(db.queryForObject("SELECT COUNT(*) FROM daily_user_activity WHERE user_id=?",Long.class,a.id())).isEqualTo(count); }
    private ResponseEntity<JsonNode> call(HttpMethod method,String path,User user,Object body) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(user!=null) headers.setBearerAuth(user.token());
        return http.exchange("/api/v1"+path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private JsonNode ok(ResponseEntity<JsonNode> response,int status) {
        assertThat(response.getStatusCode().value()).as(response.toString()).isEqualTo(status);
        assertThat(response.getBody().path("code").asText()).isEqualTo(status<400 ? "OK" : switch(status) {
            case 400->"VALIDATION_ERROR"; case 401->"UNAUTHENTICATED"; case 403->"FORBIDDEN"; case 404->"NOT_FOUND"; case 409->"CONFLICT"; default->"";
        });
        return response.getBody().path("data");
    }
}
