package com.jd.genie.platform.conversation.exception;

import com.jd.genie.platform.contract.MvpErrorCode;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DuplicateConstraintClassifier {
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;
    private static final String MYSQL_INTEGRITY_CONSTRAINT_VIOLATION = "23000";
    private static final Pattern DUPLICATE_KEY_PATTERN = Pattern.compile("for key ['`\\\"]([^'`\\\"]+)['`\\\"]");

    private DuplicateConstraintClassifier() {
    }

    public static Optional<MvpErrorCode> classify(Throwable throwable) {
        SQLException duplicate = findDuplicateSqlException(throwable);
        if (duplicate == null) {
            return Optional.empty();
        }
        return Optional.of(mapConstraint(extractConstraintName(duplicate.getMessage())));
    }

    static String extractConstraintName(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = DUPLICATE_KEY_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String key = matcher.group(1);
        int dot = key.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < key.length()) {
            return key.substring(dot + 1);
        }
        return key;
    }

    private static SQLException findDuplicateSqlException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                SQLException duplicate = findDuplicateSqlException(sqlException);
                if (duplicate != null) {
                    return duplicate;
                }
            }
            current = current.getCause();
        }
        return null;
    }

    private static SQLException findDuplicateSqlException(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current.getErrorCode() == MYSQL_DUPLICATE_ENTRY
                && MYSQL_INTEGRITY_CONSTRAINT_VIOLATION.equals(current.getSQLState())) {
                return current;
            }
            current = current.getNextException();
        }
        return null;
    }

    private static MvpErrorCode mapConstraint(String constraintName) {
        if ("uk_msg_request_role".equals(constraintName)) {
            return MvpErrorCode.DUPLICATE_REQUEST;
        }
        if ("uk_msg_turn_role".equals(constraintName)) {
            return MvpErrorCode.MESSAGE_STATE_CONFLICT;
        }
        return MvpErrorCode.INTERNAL_ERROR;
    }
}