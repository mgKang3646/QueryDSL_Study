package study.querydsl.entity;


import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.TypedQuery;
import net.bytebuddy.agent.builder.AgentBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;

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

    @Test
    public void simpleProjection(){
        List<String> fetch = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : fetch) {
            System.out.println("s = " + s);
        }
    }

    /*
    * 튜플은 리포지토리 계층 안에서만 사용, 서비스나 컨트롤러로 던질거면 DTO로 변환해서 던져야 한다.
    * 튜플은 리포지토리 계층에서 사용하는 개념임, 서비스나 컨트롤러가 리포지토리 계층의 개념에 의존하는 거는 좋지 못함.
    */
    @Test
    public void tupleProjection(){
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }


    @Test
    public void findDtoByJPQL(){
        List<MemberDto> result = em.createQuery("SELECT new study.querydsl.dto.MemberDto(m.username,m.age) FROM Member m", MemberDto.class).getResultList();
        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }


    @Test //수정자로 주입하여 MemberDTO객체 생성하기 ( 디폴트 생성자가 필요 )
    public void findDtoBySetter(){
        List<MemberDto> result = queryFactory
                .select(Projections.bean(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test //필드에 직접 주입하여 MemberDTO 객체 생성하기
    public void findDtoByField(){
        List<MemberDto> result = queryFactory
                .select(Projections.fields(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test //생성자 파라미터로 MemberDto 객체 생성하기
    public void findDtoByConstructor(){
        List<MemberDto> result = queryFactory
                .select(Projections.constructor(MemberDto.class,
                        member.username,
                        member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }
    }

    @Test
    public void findUserDto(){
        QMember memberSub = new QMember("memberSub");
        List<UserDto> result = queryFactory
                .select(Projections.fields(UserDto.class,
                        member.username.as("name"), // username과 name은 이름이 달라서 주입이 안된다. 그런경우 alias를 넣어주어 주입한다.

                        ExpressionUtils.as(JPAExpressions // 프로젝션이 서브쿼리인 경우 alias 주는 방법
                                .select(member.age.max())
                                .from(memberSub),"age")
                        ))
                .from(member)
                .fetch();

        for (UserDto userDto : result) {
            System.out.println("userDto = " + userDto);
        }
    }

    // Projections.constructor 과 비슷하지만 컴파일 오류를 더 많이 잡아 낼 수 있는 장점이 있다.
    //커맨드 + P를 누르면 파라미터 정보도 나온다.
    // 단점 : DTO는 여러 환경을 돌아다니는 객체이다. 그런데 DTO가 QueryDSL 의존하면 다른 환경에서 문제를 일으킬 수 있다.
    @Test
    public void findDtoByQueryProjection(){
        List<MemberDto> result = queryFactory
                .select(new QMemberDto(member.username, member.age))
                .from(member)
                .fetch();

        for (MemberDto memberDto : result) {
            System.out.println("memberDto = " + memberDto);
        }

    }

    // 동적 쿼리 해결하기
    // 1) BooleanBuilder
    // 2) WHERE절 다중 파라미터 사용

    @Test // BooleanBuilder 방식
    public void dynamicQuery_BooleanBuilder(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond){
        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond)); // 초기값 사용 가능
        if(usernameCond != null){
            builder.and(member.username.eq(usernameCond));
        }
        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }

        return queryFactory.selectFrom(member)
                .where(builder)
                .fetch();
    }


    //BooleanBuilder 방식은 코드가 복잡하다.
    //WHERE절 방식은 메소드를 분리하기 때문에 코드가 추상화되고 단순해진다.
    //분리된 메소드는 조립(합성)이 가능하여 단순한 쿼리를 만들 수 있다.
    //
    @Test
    public void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam,ageParam);
        assertThat(result.size()).isEqualTo(1);

    }
    private List<Member> searchMember2(String usernameCond, Integer ageCond) {
        return queryFactory
            .selectFrom(member)
            .where(allEq(usernameCond,ageCond))
            .fetch();

    }

    private BooleanExpression ageEq(Integer ageCond) {
        return ageCond != null ? member.age.eq(ageCond) : null;
    }

    private BooleanExpression usernameEq(String usernameCond) {
        return usernameCond != null ? member.username.eq(usernameCond) : null;

    }

    // 메소드1 : 광고상태가 isValid
    // 메소등2 : 날짜가 IN
    // 메소드3 : 서비스가능? ( 메소드1 + 메소등 2 )
    // 즉 코드 재활용이 가능해진다.
    private BooleanExpression allEq(String usernameCond,Integer ageCond){
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    //수정 삭제 배치 쿼리
    @Test
    public void bulkUpdate(){
        long count = queryFactory
                .update(member)
                .set(member.username,"비회원")
                .where(member.age.lt(28))
                .execute();

        //벌크연산은 영속성 컨텍스트를 무시하고 바로 DB에 실행된다.
        //영속성 컨텍스트의 데이터가 항상 우선권을 갖고 이기에 데이터 정합성이 깨진다.
        //그러므로 영속성컨텍스트를 완전히 초기화 해놓는 것이 좋다.

        em.flush();
        em.clear();
    }

    @Test
    public void bulkAdd(){
        long count = queryFactory
                .update(member)
                .set(member.age,member.age.add(1)) //member.age.multiply(2)
                .execute();
    }

    @Test
    public void bulkDelete(){
        long count = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }
    
    @Test //Dialect에 등록된 function만 사용가능
    public void sqlFunction(){
        List<String> result = queryFactory
                .select(Expressions.stringTemplate("function('replace',{0},{1},{2})",
                        member.username, "member", "M"))
                .from(member)
                .fetch();
        for (String s : result) {
            System.out.println("s = " + s);
        }

    }

    @Test
    public void sqlFunction2(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower',{0})", member.username)))
                .where(member.username.eq(member.username.lower()))
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }
}
