package com.bereta.notifications;

/** Textes en français des SMS et e-mails d'authentification. */
public final class AuthMessages {

    private AuthMessages() {
    }

    public static String loginCode(String code) {
        return "BERETA : votre code de connexion est " + code
                + ". Il expire dans 5 minutes. Ne le communiquez à personne.";
    }

    public static String phoneVerificationCode(String code) {
        return "BERETA : votre code de vérification est " + code
                + ". Il expire dans 5 minutes. Ne le communiquez à personne.";
    }

    public static String passwordResetCode(String code) {
        return "BERETA : code pour choisir un nouveau mot de passe : " + code
                + ". Il expire dans 5 minutes. Si vous n’avez rien demandé, ignorez ce message.";
    }

    public static final String EMAIL_VERIFICATION_SUBJECT = "Vérifiez votre adresse e-mail BERETA";

    public static String emailVerificationBody(String code) {
        return "Bonjour,\n\nVoici votre code de vérification BERETA : " + code
                + "\n\nIl expire dans 5 minutes.\n\nSi vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail.";
    }

    public static final String PASSWORD_RESET_SUBJECT = "Réinitialiser votre mot de passe BERETA";

    public static String passwordResetLinkBody(String url) {
        return "Bonjour,\n\nUne demande de nouveau mot de passe a été faite pour votre compte BERETA.\n\n"
                + "Ouvrez ce lien (valable 15 minutes) :\n" + url
                + "\n\nSi vous n’êtes pas à l’origine de cette demande, ignorez cet e-mail.";
    }

    public static final String PASSWORD_CHANGED_SUBJECT = "Votre mot de passe BERETA a été modifié";

    public static final String PASSWORD_CHANGED_SMS =
            "BERETA : votre mot de passe vient d’être modifié et vos appareils ont été déconnectés. "
                    + "Si ce n’est pas vous, contactez votre responsable.";

    public static final String PASSWORD_CHANGED_EMAIL =
            "Bonjour,\n\nLe mot de passe de votre compte BERETA vient d’être modifié et tous vos appareils "
                    + "ont été déconnectés.\n\nSi vous n’êtes pas à l’origine de ce changement, contactez votre "
                    + "responsable d’association ou l’assistance BERETA.";

    public static String treasurerDesignated(String localFundName, String associationName) {
        return "BERETA : vous avez été désigné trésorier de la caisse « " + localFundName + " » ("
                + associationName + "). Votre responsable vous remet votre mot de passe provisoire.";
    }

    public static final String TEMPORARY_PASSWORD_ISSUED =
            "BERETA : votre responsable a réinitialisé votre mot de passe. Il vous remet un mot de passe "
                    + "provisoire à changer à la prochaine connexion.";
}
