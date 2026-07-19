-- Marketplace author profile images + public profile table
-- Run this once in Supabase SQL editor.

-- 1) Public profile table keyed by auth user id.
create table if not exists public.marketplace_author_profiles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    display_name text not null default 'Discord user',
    avatar_url text,
    updated_at timestamptz not null default now()
);

alter table public.marketplace_author_profiles enable row level security;

-- Public read for marketplace browsing.
drop policy if exists "Public can read marketplace author profiles" on public.marketplace_author_profiles;
create policy "Public can read marketplace author profiles"
on public.marketplace_author_profiles
for select
to anon, authenticated
using (true);

-- Users can upsert only their own profile record.
drop policy if exists "Users can manage own marketplace author profile" on public.marketplace_author_profiles;
create policy "Users can manage own marketplace author profile"
on public.marketplace_author_profiles
for all
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

-- Keep marketplace presets linked to profile avatar URL.
alter table public.marketplace_presets
    add column if not exists author_avatar_url text;

update public.marketplace_presets p
set author_avatar_url = map.avatar_url
from public.marketplace_author_profiles map
where p.author_user_id = map.user_id
  and (p.author_avatar_url is null or p.author_avatar_url = '');

-- Optional helper RPC to sync profile + preset avatar fields after login/profile updates.
create or replace function public.sync_marketplace_author_profile(
    p_display_name text,
    p_avatar_url text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'Not authenticated';
    end if;

    insert into public.marketplace_author_profiles (user_id, display_name, avatar_url, updated_at)
    values (
        auth.uid(),
        coalesce(nullif(trim(p_display_name), ''), 'Discord user'),
        nullif(trim(p_avatar_url), ''),
        now()
    )
    on conflict (user_id) do update
    set display_name = excluded.display_name,
        avatar_url = excluded.avatar_url,
        updated_at = now();

    update public.marketplace_presets
    set author_name = coalesce(nullif(trim(p_display_name), ''), author_name),
        author_avatar_url = nullif(trim(p_avatar_url), ''),
        updated_at = now()
    where author_user_id = auth.uid();
end;
$$;

revoke all on function public.sync_marketplace_author_profile(text, text) from public;
grant execute on function public.sync_marketplace_author_profile(text, text) to authenticated;

-- 2) Storage bucket for author avatars (if you want to self-host instead of Discord CDN URLs).
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'marketplace-author-avatars',
    'marketplace-author-avatars',
    true,
    2097152,
    array['image/png','image/jpeg','image/webp']
)
on conflict (id) do nothing;

-- RLS for avatar uploads/updates.
drop policy if exists "Public read author avatar objects" on storage.objects;
create policy "Public read author avatar objects"
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'marketplace-author-avatars');

drop policy if exists "Users upload own author avatar object" on storage.objects;
create policy "Users upload own author avatar object"
on storage.objects
for insert
to authenticated
with check (
    bucket_id = 'marketplace-author-avatars'
    and split_part(name, '/', 1) = auth.uid()::text
);

drop policy if exists "Users update own author avatar object" on storage.objects;
create policy "Users update own author avatar object"
on storage.objects
for update
to authenticated
using (
    bucket_id = 'marketplace-author-avatars'
    and split_part(name, '/', 1) = auth.uid()::text
)
with check (
    bucket_id = 'marketplace-author-avatars'
    and split_part(name, '/', 1) = auth.uid()::text
);

drop policy if exists "Users delete own author avatar object" on storage.objects;
create policy "Users delete own author avatar object"
on storage.objects
for delete
to authenticated
using (
    bucket_id = 'marketplace-author-avatars'
    and split_part(name, '/', 1) = auth.uid()::text
);
