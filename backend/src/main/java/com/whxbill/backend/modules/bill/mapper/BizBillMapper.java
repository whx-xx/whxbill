package com.whxbill.backend.modules.bill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whxbill.backend.modules.bill.entity.BizBill;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BizBillMapper extends BaseMapper<BizBill> {

    @Select("""
        select coalesce(sum(case when bill_type = 'EXPENSE' then amount else 0 end), 0) as expense,
               coalesce(sum(case when bill_type = 'INCOME' then amount else 0 end), 0) as income
        from biz_bill
        where user_id = #{userId} and date_format(bill_date, '%Y-%m') = #{month} and deleted = 0
        """)
    Map<String, BigDecimal> selectMonthlySummary(Long userId, String month);

    @Select("""
        select c.category_name as name, sum(b.amount) as value
        from biz_bill b
        left join biz_category c on b.category_id = c.id
        where b.user_id = #{userId} and b.bill_type = 'EXPENSE' and date_format(b.bill_date, '%Y-%m') = #{month} and b.deleted = 0
        group by c.category_name
        order by value desc
        limit 10
        """)
    List<Map<String, Object>> selectTopExpenseCategories(Long userId, String month);

    @Select("""
        select date_format(bill_date, '%Y-%m-%d') as day, 
               sum(case when bill_type = 'EXPENSE' then amount else 0 end) as expense,
               sum(case when bill_type = 'INCOME' then amount else 0 end) as income
        from biz_bill
        where user_id = #{userId} and date_format(bill_date, '%Y-%m') = #{month} and deleted = 0
        group by date_format(bill_date, '%Y-%m-%d')
        order by day asc
        """)
    List<Map<String, Object>> selectDailyTrend(Long userId, String month);

    // coalesce(..., 0) 是为了防止没有数据时返回 null，统一返回 0
    @Select("""
        <script>
        select coalesce(sum(case when bill_type = 'EXPENSE' then amount else 0 end), 0) as expense,
               coalesce(sum(case when bill_type = 'INCOME' then amount else 0 end), 0) as income
        from biz_bill
        where user_id = #{userId}
          and deleted = 0
          <if test="bookId != null">and book_id = #{bookId}</if>
          <if test="startDate != null">and bill_date &gt;= #{startDate}</if>
          <if test="endDate != null">and bill_date &lt;= #{endDate}</if>
        </script>
        """)
    Map<String, BigDecimal> selectSummary(
        @Param("userId") Long userId,
        @Param("bookId") Long bookId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Select("""
        <script>
        select
          <choose>
            <when test="includeChildren">
              c.id as categoryId,
              c.parent_id as parentId,
              c.icon as icon,
              case when p.id is not null then concat(p.category_name, '-', c.category_name) else c.category_name end as name,
            </when>
            <otherwise>
              coalesce(p.id, c.id) as categoryId,
              0 as parentId,
              coalesce(p.icon, c.icon) as icon,
              coalesce(p.category_name, c.category_name) as name,
            </otherwise>
          </choose>
          count(1) as count,
          sum(b.amount) as value
        from biz_bill b
        left join biz_category c on b.category_id = c.id
        left join biz_category p on c.parent_id = p.id
        where b.user_id = #{userId}
          and b.deleted = 0
          and b.bill_type = #{billType}
          <if test="bookId != null">and b.book_id = #{bookId}</if>
          <if test="startDate != null">and b.bill_date &gt;= #{startDate}</if>
          <if test="endDate != null">and b.bill_date &lt;= #{endDate}</if>
        group by
          <choose>
            <when test="includeChildren">c.id, c.parent_id, c.icon, p.id, p.category_name, c.category_name</when>
            <otherwise>coalesce(p.id, c.id), coalesce(p.icon, c.icon), coalesce(p.category_name, c.category_name)</otherwise>
          </choose>
        order by value desc
        </script>
        """)
    List<Map<String, Object>> selectCategoryStats(
        @Param("userId") Long userId,
        @Param("bookId") Long bookId,
        @Param("billType") String billType,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("includeChildren") boolean includeChildren);

    @Select("""
        <script>
        select date_format(bill_date, '%Y-%m-%d') as day,
               sum(case when bill_type = 'EXPENSE' then amount else 0 end) as expense,
               sum(case when bill_type = 'INCOME' then amount else 0 end) as income
        from biz_bill
        where user_id = #{userId}
          and deleted = 0
          <if test="bookId != null">and book_id = #{bookId}</if>
          <if test="startDate != null">and bill_date &gt;= #{startDate}</if>
          <if test="endDate != null">and bill_date &lt;= #{endDate}</if>
        group by date_format(bill_date, '%Y-%m-%d')
        order by day asc
        </script>
        """)
    List<Map<String, Object>> selectTrendByDay(
        @Param("userId") Long userId,
        @Param("bookId") Long bookId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Select("""
        <script>
        select date_format(bill_date, '%Y-%m') as day,
               sum(case when bill_type = 'EXPENSE' then amount else 0 end) as expense,
               sum(case when bill_type = 'INCOME' then amount else 0 end) as income
        from biz_bill
        where user_id = #{userId}
          and deleted = 0
          <if test="bookId != null">and book_id = #{bookId}</if>
          <if test="startDate != null">and bill_date &gt;= #{startDate}</if>
          <if test="endDate != null">and bill_date &lt;= #{endDate}</if>
        group by date_format(bill_date, '%Y-%m')
        order by day asc
        </script>
        """)
    List<Map<String, Object>> selectTrendByMonth(
        @Param("userId") Long userId,
        @Param("bookId") Long bookId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);
}
