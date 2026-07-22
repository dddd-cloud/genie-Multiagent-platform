package com.jd.genie.platform.security;

import java.util.Map;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

@Service
public class SessionRevocationService {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) { this.sessions = sessions; }

    public void revokeByUsername(String username) {
        Map<String, ? extends Session> found = sessions.findByIndexNameAndIndexValue(
            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        found.keySet().forEach(sessions::deleteById);
    }
}
