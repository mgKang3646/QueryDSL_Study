package study.querydsl.entity;


import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
public class QueryDslBasicTest {

    @Autowired
    EntityManager em;
    JPAQueryFactory queryFactory;

    @BeforeEach // 개별테스트 실행전 돌리기
    public void before(){
        queryFactory = new JPAQueryFactory(em); // 동시성 문제를 걱정하지 않아도 된다. 멀티스레드 환경에서 최적화되도록 설계되어 있어서 필드로 빼놓아도 된다.
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);
        Member member1 = new Member("member1", 10, teamA);
        Member member2 = new Member("member2", 20, teamA);
        Member member3 = new Member("member3", 30, teamB);
        Member member4 = new Member("member4", 40, teamB);
        em.persist(member1);

        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
        //초기화 em.flush(); em.clear();
        //확인
        List<Member> members = em.createQuery("select m from Member m",
                        Member.class)
                .getResultList();
        for (Member member : members) {
            System.out.println("member=" + member);
            System.out.println("-> member.team=" + member.getTeam());
        }
    }


    @Test // 기본 JPQL : 컴파일 단계에서 오류를 발견할 수 없음.
    public void startJPQL(){
        String qlString = "SELECT m FROM Member m " +
                "WHERE m.username = :username";

        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test // 기본쿼리 DSL
    public void startQuerydsl1(){
        QMember m = new QMember("m");
        Member findMember = queryFactory
                .select(m)
                .from(m)
                .where(m.username.eq("member1")) //PreparedStatement의 파라미터 바인딩 방식을 사용하기에 SQLInjection 공격으로부터 안전하다.
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test // QueryDSL Q-TYPE으로 코드 칼끔하게 만들기
    public void startQuerydsl2(){
        //QMember m = new QMember("m"); // 같은 테이블을 JOIN해야하는 경우만 선언해서 사용하기
        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1")) //PreparedStatement의 파라미터 바인딩 방식을 사용하기에 SQLInjection 공격으로부터 안전하다.
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }


    @Test // 검색조건 쿼리 (and,or,eq,ne,isNotNull ..... )
    public void search1() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1").and(member.age.eq(10))
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test // 검색조건 쿼리 (and,or,eq,ne,isNotNull ..... )
    public void search2() {
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1"), // ',' 는 and를 의미한다.
                        member.age.eq(10)
                )
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
        List<Member> fetch = queryFactory
                .selectFrom(member)
                .fetch();

        Member fetchOne = queryFactory
                .selectFrom(member)
                .fetchOne();

        Member fetchFirst = queryFactory
                .selectFrom(member)
                .fetchFirst();
        QueryResults<Member> results = queryFactory
                .selectFrom(member)
                .fetchResults();

        results.getTotal(); // count 쿼리1
        List<Member> content = results.getResults(); // content 쿼리2
        // 한가지 쿼리객체로 2가지 쿼리를 만들 수 있다.
        // 만약 카운트쿼리를 성능상의 이류로 단순화시켜야 한다면 이런 방식으로 접근하면 안된다.

        queryFactory
                .selectFrom(member)
                .fetchCount(); // SELECT 절을 count 쿼리로 바꾸는 것

    }

    /*
    * 회원정렬순서
    * 1. 회원나이 내림차순(DESC)
    * 2. 회원이름 올림차순(ASC)
    * 단 2에서 회원이름이 없으면 마지막에 출력(nulls last)
    * */
    @Test
    public void sort(){
        em.persist(new Member(null,100));
        em.persist(new Member("member5",100));
        em.persist(new Member("member6",100));

        List<Member> result = queryFactory.selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);
        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();
    }

    @Test
    public void paging(){
        List<Member> results = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(results.size()).isEqualTo(2);
    }

    @Test
    public void paging2(){
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4); // 카운트 쿼리가 따로 나가는데 성능상의 이유로 카운틔 쿼리를 단순화해야하면 따로 분리해야한다.
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);
        assertThat(queryResults.getResults().size()).isEqualTo(2);
    }

    // 여러개의 타입이 들어올 때는 Tuple타입을 사용하면 된다.
    // 실무에서는 보통 DTO를 사용한다.
    @Test
    public void aggregation(){
        List<Tuple> result = queryFactory.select(member.count(),
                member.age.sum(),
                member.age.avg(),
                member.age.max(),
                member.age.min()
        ).from(member).fetch();

        Tuple tuple = result.get(0);
        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get(member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get(member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);

    }

    /*
    * 팀의 이름과 각 팀의 평균 연령을 구해라.
    * */
    @Test
    public void group() throws Exception {
        List<Tuple> result = queryFactory.select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);
        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);
    }

    @Test
    public void join() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1","member2");

    }

    @Test //LEFT JOIN
    public void join2() throws Exception {
        List<Member> result = queryFactory
                .selectFrom(member)
                .leftJoin(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("member1","member2");
    }

    // 세타조인 ( 연관관계 없어도 조인 하는 것 )
    // 곱집합을 먼저 from절에서 만들고 WHERE 절에서 필터링 하는 방식
    @Test
    public void join3() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Member> result = queryFactory
                .select(member)
                .from(member, team) // 먼저 모든 경우의 수를 곱집합으로 만든 다음에
                .where(member.username.eq(team.name)) // WHERE절에서 필터링 하는 방식
                .fetch();

        assertThat(result)
                .extracting("username")
                .containsExactly("teamA","teamB");
    }

/*
* 회원과 팀을 조인하면서 팀이름이 teamA인 팀만 조인, 회원은 모두조회
* JPQL : SELECT m,t FROM Member m LEFT JOIN m.team t on t.name = 'teamA'
* LEFT OUTER JOIN이라 MEMBER를 기준으로 데이터를 가져옴 ( teamB는 null인 Member도 가져온 )
* INNER JOIN을 사용하면 teamA인 Member만 가지고 온다.
* 필터링 시, OUTER JOIN인 경우 ON절을 사용하고 INNER JOIN인 경우 WHERE절을 사용한다. ( ON절을 사용해도 같지만 익숙한 WHERE절 사용 )
* */
    @Test
    public void join_on_filtering() throws Exception {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

   /*
   * 연관관계 없는 엔티티 외부 조인
   * 회원의 이름이 팀이름과 같은 대상 외부 조인
   * */
    @Test // on조인
    public void join_on_no_relation() throws Exception {
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member) // Member을 가져올 건데 만약에
                .leftJoin(team).on(member.username.eq(team.name)) //Member의 username과 team의 name이 같이 Team있다면 JOIN하기
                //leftJoin(member.team, team) x, id 외래키로 join하지 않고 username이 같은 경우에만 JOIN
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;
    @Test
    public void fetchJoinNo() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isFalse(); // LAZY로딩이라 Team 가져오지 않음

    }

    @Test
    public void fetchJoinUse() throws Exception {
        em.flush();
        em.clear();

        Member findMember = queryFactory.selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();
        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("페치 조인 미적용").isTrue(); //LAZY 로딩이어도 Team을 가져옴

    }

    /*
    * 나이가 가장 많은 회원 조회
    * */
    @Test
    public void subQuery(){

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(40);
    }


    /*
     * 나이가 가장 평균이상인 회원 조회
     * */
    //WHERE절 서브쿼리
    @Test
    public void subQueryGoe(){

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(30,40);
    }


    /*
     *
     * */
    @Test //IN절 서브쿼리
    public void subQueryIn(){

        QMember memberSub = new QMember("memberSub");
        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age").containsExactly(20,30,40);
    }

    @Test //SELECT절 서브쿼리
    public void selectSubQuery(){
        QMember memberSub = new QMember("memberSub");
        List<Tuple> result = queryFactory.select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub))
                .from(member)
                .fetch();
        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }


    //FROM절 서브쿼리는 JPA가 지원하지 않는다.
    //첫번째 시도 : 서브쿼리를 JOIN으로 바꾼다.
    //두번째 시도 : 애플리케이션 쿼리를 두번 분리해서 실행한다.
    //세번째 시도 : nativeSQL을 사용한다.


    @Test
    public void basicCase(){
        List<String> result = queryFactory
                .select(member.age.when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    //Application에서 변환 가능한 부분은 변환해주어야 한다. CASE문 남용X
    @Test
    public void completeCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20살")
                        .when(member.age.between(21, 30)).then("21~30살")
                        .otherwise("기타"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }

    @Test
    public void constant(){
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void concat(){
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue())) // cast(m1_0.age as varchar)
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }




}
