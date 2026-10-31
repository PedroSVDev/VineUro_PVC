-- REVERSIÓN: vuelve a la política abierta a la clave pública (solo si la app deja de sincronizar y hay que volver atrás).
do $$
declare t text;
begin
  foreach t in array array['clientes','pedidos','precios','configuracion'] loop
    execute format('drop policy if exists %I on public.%I', t || '_clave_app', t);
    execute format('drop policy if exists %I on public.%I', t || '_anon_todo', t);
    execute format('create policy %I on public.%I for all to anon, authenticated using (true) with check (true)', t || '_anon_todo', t);
  end loop;
end $$;
