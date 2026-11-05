package com.bereta.notifications;

/** Textes en français des e-mails d'authentification (mot de passe oublié, à réactiver avec Brevo). */
public final class AuthMessages {

    private AuthMessages() {
    }

    public static final String PASSWORD_RESET_SUBJECT = "Réinitialiser votre mot de passe BERETA";

    public static String passwordResetLinkBody(String url) {
        return "Bonjour,\n\nUne demande de nouveau mot de passe a été faite pour votre compte BERETA.\n\n"
                + "Ouvrez ce lien (valable 15 minutes) :\n" + url
                + "\n\nSi vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail.";
    }
}
