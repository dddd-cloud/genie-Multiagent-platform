package com.jd.genie.platform.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.user.dto.AdminUserResponse;
import com.jd.genie.platform.user.entity.UserEntity;
import com.jd.genie.platform.user.entity.UserStatus;
import com.jd.genie.platform.user.mapper.UserMapper;
import com.jd.genie.platform.user.service.AdminUserService;
import com.jd.genie.platform.user.service.UserNotFoundException;
import com.jd.genie.platform.user.service.UserValidationException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Covers the admin listing contract: what reaches SQL for a given filter, and how the extra row used
 * for {@code hasMore} detection is trimmed before it is handed back to the caller.
 */
class AdminUserListTest {

    private static final String TENANT = "tenant-1";

    private final CapturingUserMapper mapper = new CapturingUserMapper();
    private final AdminUserService service = new AdminUserService(null, mapper, null, null, null);

    @Test
    void aBlankKeywordIsNotTurnedIntoAFilter() {
        service.list(TENANT, 1, 20, "   ", null, null);

        assertThat(mapper.keywordPattern).isNull();
        assertThat(mapper.role).isNull();
        assertThat(mapper.status).isNull();
    }

    @Test
    void likeMetacharactersInAKeywordAreEscapedSoTheyMatchLiterally() {
        service.list(TENANT, 1, 20, " a_b%c ", null, null);

        assertThat(mapper.keywordPattern).isEqualTo("%a\\_b\\%c%");
    }

    @Test
    void surroundingWhitespaceInFiltersIsToleratedAndNormalised() {
        service.list(TENANT, 1, 20, null, " ADMIN ", " DISABLED ");

        assertThat(mapper.role).isEqualTo("ADMIN");
        assertThat(mapper.status).isEqualTo("DISABLED");
    }

    @Test
    void anUnknownRoleOrStatusIsRejectedInsteadOfSilentlyIgnored() {
        assertThatThrownBy(() -> service.list(TENANT, 1, 20, null, "SUPERUSER", null))
            .isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> service.list(TENANT, 1, 20, null, null, "PAUSED"))
            .isInstanceOf(UserValidationException.class);
    }

    @Test
    void anOverlongKeywordIsRejected() {
        assertThatThrownBy(() -> service.list(TENANT, 1, 20, "x".repeat(65), null, null))
            .isInstanceOf(UserValidationException.class);
    }

    @Test
    void pageAndPageSizeBoundsAreEnforced() {
        assertThatThrownBy(() -> service.list(TENANT, 0, 20, null, null, null))
            .isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> service.list(TENANT, 1, 0, null, null, null))
            .isInstanceOf(UserValidationException.class);
        assertThatThrownBy(() -> service.list(TENANT, 1, 101, null, null, null))
            .isInstanceOf(UserValidationException.class);
    }

    @Test
    void oneExtraRowIsRequestedAndTrimmedToDetectAFurtherPage() {
        mapper.rows = users(21);

        PageResponse<AdminUserResponse> page = service.list(TENANT, 1, 20, null, null, null);

        assertThat(mapper.limit).isEqualTo(21);
        assertThat(mapper.offset).isZero();
        assertThat(page.items()).hasSize(20);
        assertThat(page.hasMore()).isTrue();
    }

    @Test
    void aPartialPageReportsNoFurtherPage() {
        mapper.rows = users(5);

        PageResponse<AdminUserResponse> page = service.list(TENANT, 2, 20, null, null, null);

        assertThat(mapper.offset).isEqualTo(20);
        assertThat(page.items()).hasSize(5);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void aMissingUserIsReportedAsNotFoundRatherThanAnEmptyResponse() {
        assertThatThrownBy(() -> service.get(TENANT, "nobody"))
            .isInstanceOf(UserNotFoundException.class);
    }

    private static List<UserEntity> users(int count) {
        return IntStream.range(0, count).mapToObj(i -> {
            UserEntity user = new UserEntity();
            user.setId("user-" + i);
            user.setTenantId(TENANT);
            user.setUsername("user-" + i);
            user.setDisplayName("User " + i);
            user.setRole(UserRole.USER);
            user.setStatus(UserStatus.ACTIVE);
            user.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            user.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
            return user;
        }).toList();
    }

    private static final class CapturingUserMapper implements UserMapper {
        private String keywordPattern;
        private String role;
        private String status;
        private int offset;
        private int limit;
        private List<UserEntity> rows = new ArrayList<>();

        @Override
        public List<UserEntity> searchByTenant(String tenantId, String keywordPattern, String role,
                                              String status, int offset, int limit) {
            this.keywordPattern = keywordPattern;
            this.role = role;
            this.status = status;
            this.offset = offset;
            this.limit = limit;
            return new ArrayList<>(rows);
        }

        @Override
        public UserEntity findByIdAndTenantId(String userId, String tenantId) {
            return rows.stream().filter(row -> row.getId().equals(userId)).findFirst().orElse(null);
        }

        @Override
        public UserEntity findByTenantIdAndUsername(String tenantId, String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UserEntity findActiveByUsername(String username) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countAll() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updateStatusByIdAndTenantId(String userId, String tenantId, String status,
                                               LocalDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int updatePasswordByIdAndTenantId(String userId, String tenantId, String passwordHash,
                                                 LocalDateTime updatedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int insert(UserEntity user) {
            throw new UnsupportedOperationException();
        }
    }
}
