-- PHANTOM GHOST Supabase schema
-- Applied to project ref nkvkgnhmearznzwkabev on 2026-11-28.
-- Keep user profile data separate from device-local AI memory and voice data.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    full_name text not null check (char_length(full_name) between 1 and 80),
    username text not null unique
        check (username = lower(username) and username ~ '^[a-z0-9_]{3,24}$'),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
revoke all on public.profiles from anon;
grant select, insert, update on public.profiles to authenticated;

drop policy if exists profiles_select_own on public.profiles;
create policy profiles_select_own
    on public.profiles for select
    to authenticated
    using ((select auth.uid()) = id);

drop policy if exists profiles_insert_own on public.profiles;
create policy profiles_insert_own
    on public.profiles for insert
    to authenticated
    with check ((select auth.uid()) = id);

drop policy if exists profiles_update_own on public.profiles;
create policy profiles_update_own
    on public.profiles for update
    to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);

create or replace function private.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
    insert into public.profiles (id, full_name, username)
    values (
        new.id,
        coalesce(nullif(btrim(new.raw_user_meta_data ->> 'full_name'), ''), 'New User'),
        lower(coalesce(nullif(btrim(new.raw_user_meta_data ->> 'username'), ''), split_part(new.email, '@', 1)))
    );
    return new;
end;
$function$;

revoke all on function private.handle_new_user() from public, anon, authenticated;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_user();

-- Remove direct Data API execution from this platform-maintenance function.
revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
