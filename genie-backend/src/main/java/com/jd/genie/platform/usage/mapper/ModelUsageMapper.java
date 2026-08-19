package com.jd.genie.platform.usage.mapper;

import com.jd.genie.platform.usage.entity.ModelUsageRecordEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ModelUsageMapper {

    String TERMINAL_COUNTS =
        "COUNT(*) AS calls, "
            + "SUM(CASE WHEN terminal_state = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_calls, "
            + "SUM(CASE WHEN terminal_state = 'FAILED' THEN 1 ELSE 0 END) AS failed_calls, "
            + "SUM(CASE WHEN terminal_state = 'INTERRUPTED' THEN 1 ELSE 0 END) AS interrupted_calls, "
            + "COALESCE(SUM(duration_ms), 0) AS total_duration_ms, "
            + "COALESCE(SUM(prompt_tokens), 0) AS prompt_tokens, "
            + "COALESCE(SUM(completion_tokens), 0) AS completion_tokens, "
            + "COALESCE(SUM(total_tokens), 0) AS total_tokens ";

    /**
     * INSERT IGNORE against uk_usage_message: a replayed terminal event for the same assistant turn
     * is dropped instead of inflating the counters.
     */
    @Insert("INSERT IGNORE INTO model_usage_record (id, tenant_id, user_id, conversation_id, request_id, "
        + "assistant_message_id, model_name, prompt_tokens, completion_tokens, total_tokens, duration_ms, "
        + "terminal_state, created_at) VALUES (#{id}, #{tenantId}, #{userId}, #{conversationId}, #{requestId}, "
        + "#{assistantMessageId}, #{modelName}, #{promptTokens}, #{completionTokens}, #{totalTokens}, "
        + "#{durationMs}, #{terminalState}, #{createdAt})")
    int insertIgnore(ModelUsageRecordEntity record);

    @Select("SELECT " + TERMINAL_COUNTS
        + "FROM model_usage_record WHERE tenant_id = #{tenantId} "
        + "AND created_at >= #{from} AND created_at < #{to}")
    UsageTotalsRow sumTenantTotals(@Param("tenantId") String tenantId,
                                   @Param("from") LocalDateTime from,
                                   @Param("to") LocalDateTime to);

    @Select("SELECT " + TERMINAL_COUNTS
        + "FROM model_usage_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
        + "AND created_at >= #{from} AND created_at < #{to}")
    UsageTotalsRow sumUserTotals(@Param("tenantId") String tenantId,
                                 @Param("userId") String userId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS calls, "
        + "SUM(CASE WHEN terminal_state = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_calls, "
        + "SUM(CASE WHEN terminal_state = 'FAILED' THEN 1 ELSE 0 END) AS failed_calls, "
        + "COALESCE(SUM(total_tokens), 0) AS total_tokens "
        + "FROM model_usage_record WHERE tenant_id = #{tenantId} "
        + "AND created_at >= #{from} AND created_at < #{to} "
        + "GROUP BY day ORDER BY day")
    List<UsageDailyRow> listTenantDaily(@Param("tenantId") String tenantId,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);

    @Select("SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day, COUNT(*) AS calls, "
        + "SUM(CASE WHEN terminal_state = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_calls, "
        + "SUM(CASE WHEN terminal_state = 'FAILED' THEN 1 ELSE 0 END) AS failed_calls, "
        + "COALESCE(SUM(total_tokens), 0) AS total_tokens "
        + "FROM model_usage_record WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
        + "AND created_at >= #{from} AND created_at < #{to} "
        + "GROUP BY day ORDER BY day")
    List<UsageDailyRow> listUserDaily(@Param("tenantId") String tenantId,
                                      @Param("userId") String userId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Select("SELECT r.user_id AS user_id, u.username AS username, u.display_name AS display_name, "
        + "COUNT(*) AS calls, "
        + "SUM(CASE WHEN r.terminal_state = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_calls, "
        + "SUM(CASE WHEN r.terminal_state = 'FAILED' THEN 1 ELSE 0 END) AS failed_calls, "
        + "COALESCE(SUM(r.duration_ms), 0) AS total_duration_ms, "
        + "COALESCE(SUM(r.total_tokens), 0) AS total_tokens "
        + "FROM model_usage_record r LEFT JOIN app_user u ON u.id = r.user_id AND u.tenant_id = r.tenant_id "
        + "WHERE r.tenant_id = #{tenantId} AND r.created_at >= #{from} AND r.created_at < #{to} "
        + "GROUP BY r.user_id, u.username, u.display_name "
        + "ORDER BY calls DESC, r.user_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<UsageUserAggregateRow> listUserAggregates(@Param("tenantId") String tenantId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);
}
