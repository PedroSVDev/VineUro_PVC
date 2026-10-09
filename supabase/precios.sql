-- =====================================================================
-- VINEURO PVC - Tabla "precios"
-- Guarda TODO el apartado de Precios (moneda, impuesto, planes, sistemas,
-- cristal, umbrales, muebles, extras, trabajos, mosquitero, laminación y
-- colores) como un único documento JSON en la columna "data".
-- Solo existe una fila: id = 'general'.
--
-- Ejecutar una sola vez en Supabase > SQL Editor > New query > Run.
-- =====================================================================

create table if not exists public.precios (
    id                  text primary key,
    data                jsonb  not null,
    fecha_actualizacion int8   not null
);

-- Acceso para la app (usa la clave anon, igual que clientes y pedidos)
alter table public.precios enable row level security;

drop policy if exists "precios_anon_todo" on public.precios;
create policy "precios_anon_todo"
    on public.precios
    for all
    to anon, authenticated
    using (true)
    with check (true);

grant select, insert, update, delete on public.precios to anon, authenticated;
