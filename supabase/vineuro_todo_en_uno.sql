-- =====================================================================
-- VINEURO PVC - Base de datos completa (SEGURO: se puede ejecutar varias veces)
-- Crea lo que falte y agrega las columnas que falten. NO borra datos.
-- Supabase > SQL Editor > New query > pegar todo > Run
--
-- Tablas que usa la app:
--   clientes       -> clientes (nombres, apellidos, teléfono, correo, dirección, nota)
--   pedidos        -> pedidos; todas las ventanas van dentro de la columna "items" (jsonb)
--   precios        -> todo el apartado de Precios en una sola fila (id = 'general')
--   configuracion  -> datos de la empresa y términos y condiciones
-- =====================================================================

-- ---------- 1. CLIENTES ----------
create table if not exists public.clientes (
    id             text primary key,
    nombres        text not null default '',
    apellidos      text not null default '',
    telefono       text,
    email          text,
    direccion      text,
    nota           text,
    fecha_registro int8 not null default (extract(epoch from now()) * 1000)::int8
);
alter table public.clientes add column if not exists telefono       text;
alter table public.clientes add column if not exists email          text;
alter table public.clientes add column if not exists direccion      text;
alter table public.clientes add column if not exists nota           text;
alter table public.clientes add column if not exists fecha_registro int8;
-- El DNI ya no se usa: los clientes se identifican por id
alter table public.clientes drop column if exists dni;

-- ---------- 2. PEDIDOS ----------
create table if not exists public.pedidos (
    id                  text primary key,
    codigo              text,
    cliente_id          text,
    cliente_nombre      text,
    items               jsonb not null default '[]'::jsonb,
    total_ventanas      int4,
    area_total_m2       numeric,
    estado              text,
    fecha_creacion      int8,
    fecha_actualizacion int8
);
alter table public.pedidos add column if not exists codigo              text;
alter table public.pedidos add column if not exists cliente_id          text;
alter table public.pedidos add column if not exists cliente_nombre      text;
alter table public.pedidos add column if not exists items               jsonb;
alter table public.pedidos add column if not exists total_ventanas      int4;
alter table public.pedidos add column if not exists area_total_m2       numeric;
alter table public.pedidos add column if not exists estado              text;
alter table public.pedidos add column if not exists fecha_creacion      int8;
alter table public.pedidos add column if not exists fecha_actualizacion int8;

-- Pedidos antiguos que usaban el DNI: pasarlos al id del cliente y quitar el DNI
do $$
begin
  if exists (select 1 from information_schema.columns
             where table_schema='public' and table_name='pedidos' and column_name='cliente_dni') then
    execute 'update public.pedidos p set cliente_id = c.id::text from public.clientes c
             where p.cliente_id is null and c.dni::text = p.cliente_dni::text';
  end if;
exception when undefined_column then null;
end $$;
alter table public.pedidos drop column if exists cliente_dni;

-- ---------- 3. PRECIOS ----------
create table if not exists public.precios (
    id                  text primary key,
    data                jsonb not null,
    fecha_actualizacion int8  not null
);

-- ---------- 4. CONFIGURACION ----------
create table if not exists public.configuracion (
    id                  text primary key,
    data                jsonb not null,
    fecha_actualizacion int8  not null
);

-- ---------- 5. PERMISOS PARA LA APP (clave anon) ----------
-- Por ahora la app entra sin login. El endurecimiento de seguridad queda para el final.
do $$
declare t text;
begin
  foreach t in array array['clientes','pedidos','precios','configuracion'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_anon_todo', t);
    execute format('create policy %I on public.%I for all to anon, authenticated using (true) with check (true)', t || '_anon_todo', t);
    execute format('grant select, insert, update, delete on public.%I to anon, authenticated', t);
  end loop;
end $$;

-- ---------- 6. VERIFICACIÓN: debe listar las 4 tablas con sus columnas ----------
select table_name, column_name, data_type
  from information_schema.columns
 where table_schema = 'public'
   and table_name in ('clientes', 'pedidos', 'precios', 'configuracion')
 order by table_name, ordinal_position;
