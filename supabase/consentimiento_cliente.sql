-- A8 del Plan de Seguridad Integral: fecha del consentimiento del cliente (aviso de privacidad, Ley N.° 29733).
-- Ejecutar en Supabase > SQL Editor ANTES de instalar la nueva versión de la app. No borra ni cambia datos existentes
-- (los clientes anteriores quedan con 0 = sin consentimiento registrado).
alter table public.clientes add column if not exists consentimiento_fecha int8 not null default 0;

-- Verificación: debe listar la columna consentimiento_fecha.
select column_name, data_type from information_schema.columns
where table_schema = 'public' and table_name = 'clientes' and column_name = 'consentimiento_fecha';
