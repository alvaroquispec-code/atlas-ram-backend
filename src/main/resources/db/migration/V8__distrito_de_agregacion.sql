-- Atlas RAM · V8: distrito de agregación como columna real.
--
-- El antibiograma y el mapa agrupan cada aislamiento por el distrito de residencia del paciente o,
-- si no se informó, por el del establecimiento. Antes se calculaba en cada consulta con una subconsulta
-- (@Formula de Hibernate), imposible de indexar. Ahora se guarda y lo mantienen dos triggers, así queda
-- correcto venga el dato de donde venga: la API, la carga de CSV o un script SQL como el seed.

alter table isolate add column aggregation_district_id bigint;

update isolate i
set aggregation_district_id = coalesce(i.district_id, f.district_id)
from facility f
where f.id = i.facility_id;

alter table isolate add constraint fk_isolate_aggregation_district
    foreign key (aggregation_district_id) references district;

create index idx_isolate_aggregation
    on isolate (aggregation_district_id, microorganism_id, collection_date);

-- 1. Al insertar un aislamiento o cambiar su distrito o establecimiento.
create function isolate_set_aggregation_district() returns trigger as $$
begin
    new.aggregation_district_id := coalesce(
        new.district_id,
        (select f.district_id from facility f where f.id = new.facility_id));
    return new;
end;
$$ language plpgsql;

create trigger trg_isolate_aggregation_district
    before insert or update of district_id, facility_id on isolate
    for each row execute function isolate_set_aggregation_district();

-- 2. Si un establecimiento cambia de distrito, sus aislamientos sin distrito propio lo siguen.
create function facility_propagate_district() returns trigger as $$
begin
    update isolate
    set aggregation_district_id = new.district_id
    where facility_id = new.id and district_id is null;
    return new;
end;
$$ language plpgsql;

create trigger trg_facility_propagate_district
    after update of district_id on facility
    for each row
    when (old.district_id is distinct from new.district_id)
    execute function facility_propagate_district();
