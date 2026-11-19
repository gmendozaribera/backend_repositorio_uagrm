-- Sprint 5 (hardening): usuario.totp_secret pasa a guardarse cifrado con AES-256-GCM (imp CifradorTotp).
-- Formato "v1:" + Base64(IV 12 bytes + secreto + tag 16 bytes): un secreto Base32 de 32 caracteres ocupa 83
-- caracteres, que no caben en VARCHAR(64). 128 deja margen para secretos mas largos o una version futura del formato.
-- Los secretos que ya existen en claro NO se cifran aqui (SQL no tiene la llave): los cifra auth-service al arrancar,
-- de forma idempotente (los que ya empiezan con "v1:" no se tocan).
ALTER TABLE usuario ALTER COLUMN totp_secret TYPE VARCHAR(128);

COMMENT ON COLUMN usuario.totp_secret IS 'Secreto TOTP cifrado AES-GCM (v1:...) con TOTP_CIFRADO_KEY; NULL = sin 2FA';
