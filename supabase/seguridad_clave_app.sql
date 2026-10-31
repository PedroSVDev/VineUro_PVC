-- Seguridad de Supabase (acción A1 del Plan de Seguridad Integral) — SIN inicio de sesión.
-- La app envía la cabecera x-vineuro-key en cada solicitud; las tablas solo responden si coincide con la clave.
-- La clave pública sola (anon) ya no sirve para leer ni modificar datos.
-- Ejecutar en Supabase > SQL Editor. Después, compilar la app con la misma clave en local.properties (supabase.appKey).
do $$
declare t text;
begin
  foreach t in array array['clientes','pedidos','precios','configuracion'] loop
    execute format('alter table public.%I enable row level security', t);
    execute format('drop policy if exists %I on public.%I', t || '_anon_todo', t);
    execute format('drop policy if exists %I on public.%I', t || '_clave_app', t);
    execute format($p$create policy %I on public.%I for all to anon, authenticated
      using (coalesce(current_setting('request.headers', true)::json->>'x-vineuro-key','') = %L)
      with check (coalesce(current_setting('request.headers', true)::json->>'x-vineuro-key','') = %L)$p$,
      t || '_clave_app', t, 'CAMBIAR_POR_LA_CLAVE', 'CAMBIAR_POR_LA_CLAVE');
    execute format('grant select, insert, update, delete on public.%I to anon, authenticated', t);
  end loop;
end $$;

-- Verificación: debe listar 4 políticas *_clave_app y ninguna *_anon_todo.
select tablename, policyname from pg_policies where schemaname = 'public' order by tablename;
