-- Marketplace moderator roles + storage access.

create table if not exists public.marketplace_roles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    role text not null check (role in ('moderator', 'admin')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.marketplace_roles enable row level security;

drop policy if exists "Users can read own marketplace role" on public.marketplace_roles;
create policy "Users can read own marketplace role"
on public.marketplace_roles
for select
to authenticated
using (user_id = auth.uid());

drop policy if exists "Marketplace admins manage roles" on public.marketplace_roles;
create policy "Marketplace admins manage roles"
on public.marketplace_roles
for all
to authenticated
using (
    exists (
        select 1
        from public.marketplace_roles actor
        where actor.user_id = auth.uid()
          and actor.role = 'admin'
    )
)
with check (
    exists (
        select 1
        from public.marketplace_roles actor
        where actor.user_id = auth.uid()
          and actor.role = 'admin'
    )
);

insert into public.marketplace_roles (user_id, role, updated_at)
values ('4f1bdb60-3d3f-44ad-85ac-83f324da5e3e', 'moderator', now())
on conflict (user_id) do update
set role = excluded.role,
    updated_at = now();

create or replace function public.is_marketplace_moderator(target_user_id uuid default auth.uid())
returns boolean
language sql
security definer
set search_path = public, pg_temp
as $$
    select exists (
        select 1
        from public.marketplace_roles
        where user_id = coalesce(target_user_id, auth.uid())
          and role in ('moderator', 'admin')
    );
$$;

drop policy if exists "Marketplace moderators can read preset storage" on storage.objects;
create policy "Marketplace moderators can read preset storage"
on storage.objects
for select
to authenticated
using (
    bucket_id in ('graphs', 'private_graphs')
    and public.is_marketplace_moderator(auth.uid())
);

drop policy if exists "Marketplace moderators can insert preset storage" on storage.objects;
create policy "Marketplace moderators can insert preset storage"
on storage.objects
for insert
to authenticated
with check (
    bucket_id in ('graphs', 'private_graphs')
    and public.is_marketplace_moderator(auth.uid())
);

drop policy if exists "Marketplace moderators can update preset storage" on storage.objects;
create policy "Marketplace moderators can update preset storage"
on storage.objects
for update
to authenticated
using (
    bucket_id in ('graphs', 'private_graphs')
    and public.is_marketplace_moderator(auth.uid())
)
with check (
    bucket_id in ('graphs', 'private_graphs')
    and public.is_marketplace_moderator(auth.uid())
);

drop policy if exists "Marketplace moderators can delete preset storage" on storage.objects;
create policy "Marketplace moderators can delete preset storage"
on storage.objects
for delete
to authenticated
using (
    bucket_id in ('graphs', 'private_graphs')
    and public.is_marketplace_moderator(auth.uid())
);
