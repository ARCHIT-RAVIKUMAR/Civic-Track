package com.civictrack.security;

import com.civictrack.enums.ActivityType;
import com.civictrack.service.ActivityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Listens to Spring Security's login events: feeds the lock-out counter and the activity log. */
@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);

    private final LoginAttemptService loginAttemptService;
    private final ActivityService activityService;

    public LoginAttemptListener(LoginAttemptService loginAttemptService, ActivityService activityService) {
        this.loginAttemptService = loginAttemptService;
        this.activityService = activityService;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        String email = event.getAuthentication().getName();
        boolean nowLocked = loginAttemptService.recordFailure(email);
        try {
            activityService.recordLoginEvent(email, ActivityType.LOGIN_FAILED);
            if (nowLocked) {
                activityService.recordLoginEvent(email, ActivityType.ACCOUNT_LOCKED);
            }
        } catch (RuntimeException ex) {
            log.warn("Could not record login failure: {}", ex.getMessage()); // never block a login
        }
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String email = event.getAuthentication().getName();
        loginAttemptService.recordSuccess(email);
        try {
            activityService.recordLoginEvent(email, ActivityType.LOGIN_SUCCESS);
        } catch (RuntimeException ex) {
            log.warn("Could not record login: {}", ex.getMessage());
        }
    }
}
