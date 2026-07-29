package com.jd.genie.platform.security;

import com.jd.genie.platform.user.mapper.UserMapper;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserDetailsService implements UserDetailsService {
    private final UserMapper userMapper;
    public CurrentUserDetailsService(UserMapper userMapper) { this.userMapper = userMapper; }
    @Override public UserDetails loadUserByUsername(String username) {
        if (username == null) throw new UsernameNotFoundException("Invalid credentials");
        var user = userMapper.findActiveByUsername(username.trim().toLowerCase(Locale.ROOT));
        if (user == null) throw new UsernameNotFoundException("Invalid credentials");
        return GenieUserPrincipal.from(user);
    }
}
