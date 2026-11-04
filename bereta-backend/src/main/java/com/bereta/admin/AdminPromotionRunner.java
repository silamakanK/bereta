package com.bereta.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminPromotionRunner implements ApplicationRunner {
    private static final String PREFIX = "--bereta.admin.promote-email=";
    private final AdminService service;
    public AdminPromotionRunner(AdminService service) { this.service = service; }

    @Override public void run(ApplicationArguments args) {
        String explicitArgument = args.getSourceArgs().length == 0 ? null :
                java.util.Arrays.stream(args.getSourceArgs()).filter(arg -> arg.startsWith(PREFIX)).findFirst().orElse(null);
        if (explicitArgument == null) return;
        String email = explicitArgument.substring(PREFIX.length()).trim();
        if (email.isBlank()) throw new IllegalArgumentException("L’option --bereta.admin.promote-email exige une adresse e-mail.");
        service.promoteByEmail(email);
    }
}
