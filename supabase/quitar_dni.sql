-- =====================================================================
-- VINEURO PVC - Quitar el DNI: los clientes se identifican por "id"
-- Ejecutar UNA sola vez en Supabase > SQL Editor > New query > Run.
-- No borra clientes ni pedidos.
-- =====================================================================

-- 1. Pedidos: nueva columna cliente_id (el id del cliente)
alter table public.pedidos add column if not exists cliente_id text;

-- 2. Pasar los pedidos existentes de "DNI del cliente" a "id del cliente"
update public.pedidos p
set    cliente_id = c.id
from   public.clientes c
where  p.cliente_id is null
  and  c.dni = p.cliente_dni;

-- 3. Quitar las columnas de DNI
alter table public.pedidos  drop column if exists cliente_dni;
alter table public.clientes drop column if exists dni;
