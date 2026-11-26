package edu.szu.moments.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
class AdminInitializer implements ApplicationRunner {
    private final AuthService auth;
    private final Environment environment;

    AdminInitializer(AuthService auth, Environment environment) {
        this.auth = auth;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        String account = environment.getProperty("MOMENTS_ADMIN_ACCOUNT");
        String password = environment.getProperty("MOMENTS_ADMIN_PASSWORD");
        if ((account == null || account.isEmpty()) && (password == null || password.isEmpty())) return;
        auth.initializeAdmin(account, password, environment.getProperty("MOMENTS_ADMIN_NICKNAME", "管理员"));
    }
}
