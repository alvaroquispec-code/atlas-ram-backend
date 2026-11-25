-- Atlas RAM · V12: revocación de sesiones.
--
-- Los JWT son sin estado: un token firmado vale hasta que vence (acceso 1 h, refresco 7 días). Para
-- poder invalidarlos antes, cada token lleva la versión de sesión del usuario (claim "ver") y el
-- servidor lo rechaza si no coincide con esta columna. Cambiar o restablecer la contraseña, o pedir
-- "cerrar sesión en todos los dispositivos", incrementa la versión y deja inválidos todos los tokens
-- emitidos antes.
--
-- Se arranca en 1 y los tokens sin claim "ver" se rechazan: así también caducan las sesiones abiertas
-- antes de este cambio, incluidas las que se hubieran obtenido con la contraseña de demostración que
-- estuvo publicada.

alter table app_user add column token_version integer not null default 1
    check (token_version >= 1);
