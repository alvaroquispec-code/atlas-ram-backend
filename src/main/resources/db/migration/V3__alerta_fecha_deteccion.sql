-- Atlas RAM · V3: fecha de detección de cada alerta.
-- La alerta se actualiza mientras el brote sigue activo (ventana, cifras, valor p), así que hace falta
-- un campo que no cambie y diga cuándo se detectó por primera vez.

alter table resistance_alert add column detected_on date;
update resistance_alert set detected_on = window_end where detected_on is null;
alter table resistance_alert alter column detected_on set not null;
