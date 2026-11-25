-- Atlas RAM · V11: interpretación por difusión en disco (halo en mm).
--
-- Los laboratorios con microbiología manual (Kirby-Bauer) reportan el diámetro del halo, no la CIM.
-- El punto de corte de disco va en sentido contrario al de la CIM: un halo GRANDE es sensible.
--   S si halo >= disk_susceptible_min; R si halo <= disk_resistant_max; I entre ambos.
-- Los valores los carga el laboratorio desde la tabla vigente de CLSI M100 (cambian cada año); el
-- sistema no trae valores de disco precargados para no publicar puntos de corte desactualizados.

alter table breakpoint add column disk_susceptible_min integer check (disk_susceptible_min > 0);
alter table breakpoint add column disk_resistant_max integer check (disk_resistant_max > 0);
alter table breakpoint add constraint ck_breakpoint_disk_order
    check (disk_resistant_max is null or disk_susceptible_min is null or disk_resistant_max < disk_susceptible_min);

alter table susceptibility_result add constraint ck_result_disk_positive
    check (disk_diffusion_mm is null or disk_diffusion_mm > 0);
