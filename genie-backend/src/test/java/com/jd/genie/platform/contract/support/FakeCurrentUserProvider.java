package com.jd.genie.platform.contract.support;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;

public class FakeCurrentUserProvider implements CurrentUserProvider {

    private CurrentUser currentUser;

    public FakeCurrentUserProvider() {
    }

    public FakeCurrentUserProvider(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    public void setCurrentUser(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public CurrentUser requireCurrentUser() {
        if (currentUser == null) {
            throw new IllegalStateException("CurrentUser is not configured");
        }
        return currentUser;
    }
}
