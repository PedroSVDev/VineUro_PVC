-- Código corto visible del cliente (CLI-0001, CLI-0002...).
-- Ejecutar una sola vez en Supabase > SQL Editor. No borra ni cambia datos existentes.
alter table public.clientes
  add column if not exists codigo text not null default '';
