-- =====================================================================
-- VINEURO PVC - Script seguro (se puede ejecutar más de una vez)
-- 1) Quita el DNI de clientes y pasa los pedidos a cliente_id
-- 2) Crea la tabla "configuracion" (requisitos del documento)
-- Supabase > SQL Editor > New query > pegar todo > Run
-- =====================================================================

-- ---------- 1. PEDIDOS: columna cliente_id ----------
alter table public.pedidos add column if not exists cliente_id text;

-- Pasar los pedidos antiguos (con DNI) al id del cliente, solo si aún existen las columnas de DNI
do $$
begin
  if exists (select 1 from information_schema.columns
             where table_schema='public' and table_name='pedidos' and column_name='cliente_dni')
     and exists (select 1 from information_schema.columns
             where table_schema='public' and table_name='clientes' and column_name='dni') then
    update public.pedidos p
       set cliente_id = c.id::text
      from public.clientes c
     where p.cliente_id is null
       and c.dni = p.cliente_dni;
  end if;
end $$;

-- ---------- 2. Quitar columnas de DNI ----------
alter table public.pedidos  drop column if exists cliente_dni;
alter table public.clientes drop column if exists dni;

-- ---------- 3. Tabla CONFIGURACION ----------
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

-- ---------- 4. Verificación: columnas que quedaron ----------
select table_name, column_name, data_type, is_nullable
  from information_schema.columns
 where table_schema = 'public'
   and table_name in ('clientes', 'pedidos', 'configuracion')
 order by table_name, ordinal_position;
