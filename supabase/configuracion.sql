-- =====================================================================
-- VINEURO PVC - Tabla "configuracion"
-- Una fila por clave. Por ahora: id = 'requisitos_documento'
-- (datos de la empresa y condiciones que salen en el PDF de la oferta).
--
-- Ejecutar una sola vez en Supabase > SQL Editor > New query > Run.
-- =====================================================================

create table if not exists public.configuracion (
    id                  text primary key,
    data                jsonb not null,
    fecha_actualizacion int8  not null
);

alter table public.configuracion enable row level security;

drop policy if exists "configuracion_anon_todo" on public.configuracion;
create policy "configuracion_anon_todo"
    on public.configuracion
    for all
    to anon, authenticated
    using (true)
    with check (true);

grant select, insert, update, delete on public.configuracion to anon, authenticated;
