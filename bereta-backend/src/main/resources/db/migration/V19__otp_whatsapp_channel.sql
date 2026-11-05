-- Codes par WhatsApp (OTP_WHATSAPP_ENABLED) : nouveau canal autorisé pour otp_codes.
ALTER TABLE otp_codes DROP CONSTRAINT ck_otp_codes_channel;
ALTER TABLE otp_codes ADD CONSTRAINT ck_otp_codes_channel CHECK (channel IN ('SMS', 'WHATSAPP', 'EMAIL'));
